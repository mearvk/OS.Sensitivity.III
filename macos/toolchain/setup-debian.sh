#!/usr/bin/env bash
# Install the pinned macOS-shape (Darwin) build toolchain on a Debian/Ubuntu
# cross-build host, without Docker. Keep this set identical to the Dockerfile
# and toolchain.json.
#
# No Apple macOS image, Apple binaries, Apple SDK, or Apple developer account is
# used -- this installs only standard open-source build tooling and the open
# dev libraries PureDarwin / GNUstep / Darling link against.
#
# Usage:
#   sudo macos/toolchain/setup-debian.sh
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "setup-debian.sh must run as root (use sudo)." >&2
  exit 1
fi

PKGS=(
  ca-certificates
  build-essential make pkg-config
  clang llvm lld
  cmake ninja-build bison flex python3 git
  uuid-dev libssl-dev libxml2-dev zlib1g-dev
  libbsd-dev libblocksruntime-dev
  libffi-dev libgnutls28-dev libicu-dev
  libjpeg-dev libtiff-dev libpng-dev
  libcairo2-dev libx11-dev libxt-dev
  libfuse-dev libudev-dev libgl1-mesa-dev
  libfreetype-dev libfontconfig-dev
  libxrandr-dev libxcursor-dev
)

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends "${PKGS[@]}"

echo
echo "macOS-shape (Darwin) toolchain installed."
echo "  clang -> $(clang --version | head -1)"
echo "  cmake -> $(cmake --version | head -1)"
echo "  ninja -> $(ninja --version)"
echo
echo "For a fully pinned Darwin OS build, PureDarwin also provides a Nix flake:"
echo "  (cd macos/puredarwin/puredarwin && nix build)"
echo "swift-corelibs-foundation additionally needs a Swift toolchain (swift/swiftc)."
