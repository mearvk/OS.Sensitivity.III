#!/usr/bin/env bash
# Build a path of the Windows-compatible shape into its declared artifact.
#
# The SLeeLa OS Creator provisions the pinned toolchain (windows/toolchain/)
# and then calls this orchestrator. It wraps the two upstream build recipes and
# emits a deterministic artifact at a known location:
#
#   wine     -> an installed Wine prefix        (artifact: a directory)
#   reactos  -> bootcd.iso                       (artifact: an ISO)
#
# No Microsoft source, SDK, binaries, or license are used.
#
# Usage:
#   windows/build-shape.sh <wine|reactos> [--arch amd64|i386] [--out DIR] [--jobs N]
#
# Examples:
#   windows/build-shape.sh wine    --arch amd64 --out /out/wine
#   windows/build-shape.sh reactos --arch amd64 --out /out/reactos
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"          # windows/
WAREHOUSE="$(cd "$HERE/.." && pwd)"            # repo root

PATH_NAME="${1:-}"; shift || true
ARCH="amd64"
OUT=""
JOBS="$(nproc 2>/dev/null || echo 2)"

while [ $# -gt 0 ]; do
  case "$1" in
    --arch) ARCH="${2:?}"; shift 2;;
    --out)  OUT="${2:?}"; shift 2;;
    --jobs) JOBS="${2:?}"; shift 2;;
    *) echo "Unknown argument: $1" >&2; exit 2;;
  esac
done

case "$ARCH" in amd64|i386) ;; *) echo "--arch must be amd64 or i386" >&2; exit 2;; esac

die() { echo "build-shape: $*" >&2; exit 1; }

build_wine() {
  local src="$HERE/compat-loader/wine"
  [ -f "$src/configure" ] || die "wine configure not found at $src"
  local out="${OUT:-$WAREHOUSE/out/wine-$ARCH}"
  local build="$out/.build"
  mkdir -p "$build" "$out"

  local arch_flag
  if [ "$ARCH" = "amd64" ]; then
    arch_flag="--enable-archs=i386,x86_64"   # WoW64: 64-bit Wine that runs 32-bit apps too
  else
    arch_flag="--enable-win32"
  fi

  echo "==> Configuring Wine ($ARCH) in $build"
  ( cd "$build" && "$src/configure" $arch_flag --prefix="$out" )
  echo "==> Building Wine (-j$JOBS)"
  ( cd "$build" && make -j"$JOBS" )
  echo "==> Installing Wine prefix to $out"
  ( cd "$build" && make install )
  echo "ARTIFACT: wine prefix -> $out (run: $out/bin/wine --version)"
}

build_reactos() {
  local src="$HERE/reactos/reactos"
  [ -f "$src/configure.sh" ] || die "reactos configure.sh not found at $src"
  command -v cmake  >/dev/null || die "cmake not found (run windows/toolchain/setup-debian.sh)"
  command -v ninja  >/dev/null || die "ninja not found (run windows/toolchain/setup-debian.sh)"
  local prefix="${ARCH/amd64/x86_64-w64-mingw32-}"; prefix="${prefix/i386/i686-w64-mingw32-}"
  command -v "${prefix}gcc" >/dev/null || die "MinGW-w64 cross compiler ${prefix}gcc not found (run setup-debian.sh)"

  local out="${OUT:-$WAREHOUSE/out/reactos-$ARCH}"
  mkdir -p "$out"

  export ROS_ARCH="$ARCH"   # configure.sh fails closed without this
  echo "==> Configuring ReactOS (ROS_ARCH=$ROS_ARCH)"
  ( cd "$src" && ./configure.sh )
  local objdir="$src/output-MinGW-$ARCH"
  [ -d "$objdir" ] || objdir="$(find "$src" -maxdepth 1 -type d -name 'output-*' | head -1)"
  [ -d "$objdir" ] || die "ReactOS output directory not created by configure.sh"

  echo "==> Building ReactOS bootcd (-j$JOBS) in $objdir"
  ( cd "$objdir" && ninja -j"$JOBS" bootcd )
  local iso
  iso="$(find "$objdir" -maxdepth 2 -name 'bootcd.iso' | head -1)"
  [ -n "$iso" ] || die "bootcd.iso not produced"
  cp -f "$iso" "$out/bootcd.iso"
  echo "ARTIFACT: reactos ISO -> $out/bootcd.iso"
  echo "  Test: qemu-system-x86_64 -m 2048 -cdrom $out/bootcd.iso -boot d"
}

case "$PATH_NAME" in
  wine)    build_wine;;
  reactos) build_reactos;;
  ""|-h|--help)
    echo "Usage: windows/build-shape.sh <wine|reactos> [--arch amd64|i386] [--out DIR] [--jobs N]"
    [ -z "$PATH_NAME" ] && exit 2 || exit 0;;
  *) die "unknown path '$PATH_NAME' (expected wine or reactos)";;
esac
