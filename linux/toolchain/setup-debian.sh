#!/usr/bin/env bash
# Install the pinned native-Linux-shape build toolchain on a Debian/Ubuntu host,
# without Docker. Keep this set identical to the Dockerfile and toolchain.json.
#
# Usage:
#   sudo linux/toolchain/setup-debian.sh
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "setup-debian.sh must run as root (use sudo)." >&2
  exit 1
fi

PKGS=(
  ca-certificates
  build-essential make gcc
  flex bison bc
  libssl-dev libelf-dev libncurses-dev
  cpio kmod zstd
  debootstrap squashfs-tools xorriso
  grub-pc-bin grub-efi-amd64-bin mtools dosfstools
)

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends "${PKGS[@]}"

echo
echo "Linux-shape toolchain installed."
echo "  gcc   -> $(gcc --version | head -1)"
echo "  make  -> $(make --version | head -1)"
echo "  flex/bison present: $(command -v flex >/dev/null && command -v bison >/dev/null && echo yes || echo NO)"
echo "  xorriso (ISO authoring): $(command -v xorriso >/dev/null && echo yes || echo NO)"
