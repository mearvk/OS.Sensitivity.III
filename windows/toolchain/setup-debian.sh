#!/usr/bin/env bash
# Install the pinned Windows-shape build toolchain on a Debian/Ubuntu host
# (bare metal or CI), without Docker. Keep this set identical to the Dockerfile
# and toolchain.json.
#
# No Microsoft source, SDK, binaries, or license are used -- this installs only
# standard open-source build tooling and the open dev libraries Wine links
# against.
#
# Usage:
#   sudo windows/toolchain/setup-debian.sh
#
# After it runs, ReactOS needs ROS_ARCH exported for configure.sh:
#   export ROS_ARCH=amd64   # or i386
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "setup-debian.sh must run as root (use sudo)." >&2
  exit 1
fi

PKGS=(
  ca-certificates
  build-essential
  gcc g++ gcc-multilib g++-multilib
  flex bison make pkg-config
  cmake ninja-build
  gcc-mingw-w64-x86-64 g++-mingw-w64-x86-64
  gcc-mingw-w64-i686 g++-mingw-w64-i686
  binutils-mingw-w64-x86-64 binutils-mingw-w64-i686
  python3 git p7zip-full
  libx11-dev libfreetype-dev libfontconfig-dev
  libgl-dev libxml2-dev libxslt1-dev
  libgnutls28-dev libpng-dev libjpeg-dev
  libxcursor-dev libxrandr-dev libxi-dev
  libxrender-dev libxext-dev
)

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends "${PKGS[@]}"

echo
echo "Windows-shape toolchain installed."
echo "Verify cross compilers:"
for t in x86_64-w64-mingw32-gcc i686-w64-mingw32-gcc; do
  if command -v "$t" >/dev/null 2>&1; then
    echo "  OK  $t -> $("$t" -dumpversion 2>/dev/null || echo present)"
  else
    echo "  MISSING  $t" >&2
  fi
done
echo "  cmake  -> $(cmake --version | head -1)"
echo "  ninja  -> $(ninja --version)"
echo
echo "For ReactOS, export ROS_ARCH before running windows/build-shape.sh reactos:"
echo "  export ROS_ARCH=amd64   # or i386"
