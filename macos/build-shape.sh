#!/usr/bin/env bash
# Build a path of the macOS-compatible (Darwin) shape into its declared artifact.
#
# The SLeeLa OS Creator provisions the pinned toolchain (macos/toolchain/) and
# then calls this orchestrator. It wraps the upstream build recipes and emits a
# deterministic artifact:
#
#   darwin-os       -> puredarwin.iso         (bootable Darwin OS image)
#   frameworks      -> installed GNUstep prefix (Cocoa/Foundation surface)
#   darwin-runtime  -> installed Darling prefix (Mach-O loader / dyld)
#
# No Apple macOS image, Apple binaries, Apple SDK, or Apple developer account is
# used.
#
# Usage:
#   macos/build-shape.sh <darwin-os|frameworks|darwin-runtime> [--out DIR] [--jobs N]
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"          # macos/
WAREHOUSE="$(cd "$HERE/.." && pwd)"            # repo root

PATH_NAME="${1:-}"; shift || true
OUT=""
JOBS="$(nproc 2>/dev/null || echo 2)"

while [ $# -gt 0 ]; do
  case "$1" in
    --out)  OUT="${2:?}"; shift 2;;
    --jobs) JOBS="${2:?}"; shift 2;;
    *) echo "Unknown argument: $1" >&2; exit 2;;
  esac
done

die() { echo "build-shape: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null || die "$1 not found (run macos/toolchain/setup-debian.sh)"; }

build_darwin_os() {
  local src="$HERE/puredarwin/puredarwin"
  [ -f "$src/CMakeLists.txt" ] || die "PureDarwin CMakeLists.txt not found at $src"
  need cmake; need ninja
  local out="${OUT:-$WAREHOUSE/out/macos-darwin-os}"
  mkdir -p "$out"
  echo "==> Configuring PureDarwin (CMake/Ninja)"
  ( cd "$src" && cmake -G Ninja -B build -S . -DCMAKE_BUILD_TYPE=Release )
  echo "==> Building Darwin userland + kernel integration (-j$JOBS)"
  ( cd "$src" && ninja -C build -j"$JOBS" )
  echo "==> Assembling bootable image"
  ( cd "$src" && ninja -C build image )
  local iso
  iso="$(find "$src/build" -maxdepth 2 -name '*.iso' | head -1)"
  [ -n "$iso" ] || die "no bootable image (*.iso) produced"
  cp -f "$iso" "$out/puredarwin.iso"
  echo "ARTIFACT: darwin OS image -> $out/puredarwin.iso"
  echo "  Test: qemu-system-x86_64 -m 2048 -cdrom $out/puredarwin.iso -boot d"
}

build_frameworks() {
  need cmake
  local fw="$HERE/frameworks"
  local out="${OUT:-$WAREHOUSE/out/macos-frameworks}"
  mkdir -p "$out"
  # Build in dependency order: runtime -> make -> base -> corebase -> gui -> back
  echo "==> Building GNUstep Objective-C runtime (libobjc2)"
  ( cd "$fw/gnustep-libobjc2" && cmake -B build -S . -DCMAKE_BUILD_TYPE=Release && cmake --build build -j"$JOBS" && cmake --install build --prefix "$out" )
  for mod in gnustep-make gnustep-base gnustep-corebase gnustep-gui gnustep-back; do
    if [ -x "$fw/$mod/configure" ] || [ -f "$fw/$mod/configure" ]; then
      echo "==> Building $mod"
      ( cd "$fw/$mod" && ./configure --prefix="$out" && make -j"$JOBS" && make install )
    else
      echo "   (skip $mod: no configure present in this snapshot)"
    fi
  done
  echo "ARTIFACT: GNUstep framework prefix -> $out"
}

build_darwin_runtime() {
  local src="$HERE/compat-loader/darling"
  [ -f "$src/CMakeLists.txt" ] || die "Darling CMakeLists.txt not found at $src"
  need cmake; need ninja
  local out="${OUT:-$WAREHOUSE/out/macos-darling}"
  mkdir -p "$out/build"
  echo "==> NOTE: a full Darling build initializes upstream git submodules first."
  echo "==> Configuring Darling (CMake/Ninja)"
  ( cd "$out/build" && cmake "$src" -DCMAKE_BUILD_TYPE=Release -GNinja -DCMAKE_INSTALL_PREFIX="$out" )
  echo "==> Building Darling runtime (-j$JOBS)"
  ( cd "$out/build" && ninja -j"$JOBS" )
  echo "ARTIFACT: Darling runtime prefix -> $out (ninja install to deploy)"
}

case "$PATH_NAME" in
  darwin-os)      build_darwin_os;;
  frameworks)     build_frameworks;;
  darwin-runtime) build_darwin_runtime;;
  ""|-h|--help)
    echo "Usage: macos/build-shape.sh <darwin-os|frameworks|darwin-runtime> [--out DIR] [--jobs N]"
    [ -z "$PATH_NAME" ] && exit 2 || exit 0;;
  *) die "unknown path '$PATH_NAME' (expected darwin-os, frameworks, or darwin-runtime)";;
esac
