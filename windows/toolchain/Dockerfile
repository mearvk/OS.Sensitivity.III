# Pinned, reproducible build toolchain for the Windows-compatible shape.
#
# The SLeeLa OS Creator provisions this image, then runs windows/build-shape.sh
# inside it to compile a chosen path (Wine or ReactOS) into its artifact.
#
# No Microsoft source, SDK, binaries, or license are used. Everything here is
# standard open-source build tooling (GCC, MinGW-w64, CMake, Ninja, flex,
# bison) plus the open dev libraries Wine links against.
#
# Base is ubuntu:24.04 to match the Ubuntu 24.04.4 base rootfs assembled under
# linux/os-loader, so the Windows shape builds against the same userland the
# OS Creator ships.

FROM ubuntu:24.04

LABEL org.sleela.shape="windows" \
      org.sleela.toolchain="1.0" \
      org.sleela.compliance="clean-room; no Microsoft code, SDK, or license"

ENV DEBIAN_FRONTEND=noninteractive \
    LC_ALL=C.UTF-8 \
    LANG=C.UTF-8

# Keep the dependency set identical to setup-debian.sh and toolchain.json.
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates \
      build-essential \
      gcc g++ gcc-multilib g++-multilib \
      flex bison make pkg-config \
      cmake ninja-build \
      gcc-mingw-w64-x86-64 g++-mingw-w64-x86-64 \
      gcc-mingw-w64-i686 g++-mingw-w64-i686 \
      binutils-mingw-w64-x86-64 binutils-mingw-w64-i686 \
      python3 git p7zip-full \
      libx11-dev libfreetype-dev libfontconfig-dev \
      libgl-dev libxml2-dev libxslt1-dev \
      libgnutls28-dev libpng-dev libjpeg-dev \
      libxcursor-dev libxrandr-dev libxi-dev \
      libxrender-dev libxext-dev \
    && rm -rf /var/lib/apt/lists/*

# The warehouse is mounted/copied in by the OS Creator at /src.
WORKDIR /src
CMD ["/bin/bash", "-lc", "echo 'Windows-shape toolchain ready. Run windows/build-shape.sh <wine|reactos>.'"]
