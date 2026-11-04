# Pinned, reproducible build toolchain for the macOS-compatible (Darwin) shape.
#
# Built on a LINUX cross-build host. The SLeeLa OS Creator provisions this image
# and runs macos/build-shape.sh to compile a chosen path (Darwin OS via
# PureDarwin, the GNUstep framework surface, or the Darling runtime).
#
# No Apple macOS image, Apple binaries, Apple SDK, or Apple developer account is
# used. XNU is public APSL-2.0 source; PureDarwin/GNUstep/Darling are openly
# licensed. Base is ubuntu:24.04 to match the linux/ base the OS Creator ships.

FROM ubuntu:24.04

LABEL org.sleela.shape="macos" \
      org.sleela.toolchain="1.0" \
      org.sleela.compliance="Darwin-based; no Apple image, SDK, or developer account"

ENV DEBIAN_FRONTEND=noninteractive \
    LC_ALL=C.UTF-8 \
    LANG=C.UTF-8

# Union of the per-path dependency sets in toolchain.json (PureDarwin + GNUstep
# + Darling). Keep in lockstep with setup-debian.sh and toolchain.json.
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates \
      build-essential make pkg-config \
      clang llvm lld \
      cmake ninja-build bison flex python3 git \
      uuid-dev libssl-dev libxml2-dev zlib1g-dev \
      libbsd-dev libblocksruntime-dev \
      libffi-dev libgnutls28-dev libicu-dev \
      libjpeg-dev libtiff-dev libpng-dev \
      libcairo2-dev libx11-dev libxt-dev \
      libfuse-dev libudev-dev libgl1-mesa-dev \
      libfreetype-dev libfontconfig-dev \
      libxrandr-dev libxcursor-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
CMD ["/bin/bash", "-lc", "echo 'macOS-shape toolchain ready. Run macos/build-shape.sh <darwin-os|frameworks|darwin-runtime>.'"]
