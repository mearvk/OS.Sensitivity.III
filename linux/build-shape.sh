#!/usr/bin/env bash
# Build the native Linux OS shape into its declared artifact.
#
# The SLeeLa OS Creator provisions the pinned toolchain (linux/toolchain/) and
# then calls this orchestrator. It wraps the component recipes in boot order and
# emits a deterministic artifact:
#
#   kernel  -> bzImage                         (the compiled Linux kernel)
#   rootfs  -> an assembled Ubuntu root filesystem (kernel + systemd + GRUB)
#   iso     -> a bootable, installable image
#
# Usage:
#   linux/build-shape.sh <kernel|rootfs|iso> [--out DIR] [--jobs N]
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"          # linux/
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
need() { command -v "$1" >/dev/null || die "$1 not found (run linux/toolchain/setup-debian.sh)"; }

KSRC="$HERE/os-core/linux-5.10.254"

build_kernel() {
  [ -f "$KSRC/Makefile" ] || die "kernel Makefile not found at $KSRC"
  need make; need gcc; need flex; need bison
  local out="${OUT:-$WAREHOUSE/out/linux-kernel}"
  mkdir -p "$out"
  echo "==> Configuring kernel (defconfig)"
  ( cd "$KSRC" && make x86_64_defconfig 2>/dev/null || make defconfig )
  echo "==> Building kernel (-j$JOBS)"
  ( cd "$KSRC" && make -j"$JOBS" )
  local bz="$KSRC/arch/x86/boot/bzImage"
  [ -f "$bz" ] || die "bzImage not produced"
  cp -f "$bz" "$out/bzImage"
  echo "ARTIFACT: kernel -> $out/bzImage"
}

build_rootfs() {
  local userland="$HERE/os-loader/userland"
  local tar
  tar="$(find "$userland" -maxdepth 1 -name 'ubuntu-base-*-amd64.tar.gz' | head -1)"
  [ -n "$tar" ] || die "base rootfs tarball not found under $userland"
  local out="${OUT:-$WAREHOUSE/out/linux-rootfs}"
  local root="$out/_rootfs"
  mkdir -p "$root"
  echo "==> Extracting base rootfs: $(basename "$tar")"
  tar -xzf "$tar" -C "$root"
  echo "==> NOTE: completing the system needs a chroot with networking:"
  echo "    sudo cp /etc/resolv.conf $root/etc/"
  echo "    sudo chroot $root /bin/bash -c 'apt-get update && \\"
  echo "        apt-get install -y --no-install-recommends \\"
  echo "        linux-image-generic systemd-sysv grub-pc-bin grub-efi-amd64-bin initramfs-tools'"
  echo "  (This is the native apt provision step; see linux/provision/provision.json"
  echo "   for the first-boot variant that defers it to the installed system.)"
  echo "ARTIFACT: staged rootfs -> $root"
}

build_iso() {
  need xorriso
  die "ISO assembly wraps a built kernel + completed rootfs + GRUB; run 'kernel' and 'rootfs' first, then author with xorriso/grub-mkrescue. (Recipe documented in linux/BUILD.md.)"
}

case "$PATH_NAME" in
  kernel) build_kernel;;
  rootfs) build_rootfs;;
  iso)    build_iso;;
  ""|-h|--help)
    echo "Usage: linux/build-shape.sh <kernel|rootfs|iso> [--out DIR] [--jobs N]"
    [ -z "$PATH_NAME" ] && exit 2 || exit 0;;
  *) die "unknown path '$PATH_NAME' (expected kernel, rootfs, or iso)";;
esac
