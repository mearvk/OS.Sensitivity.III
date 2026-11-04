# Pinned, reproducible build toolchain for the native Linux OS shape.
#
# The SLeeLa OS Creator provisions this image and runs linux/build-shape.sh to
# compile the Linux 5.10.254 kernel and assemble a bootable Ubuntu 24.04.4 root
# filesystem into an installable artifact.
#
# Base is ubuntu:24.04 to match the Ubuntu 24.04.4 base rootfs this shape ships,
# so the kernel and userland build against the same userland the OS Creator
# delivers.

FROM ubuntu:24.04

LABEL org.sleela.shape="linux" \
      org.sleela.toolchain="1.0"

ENV DEBIAN_FRONTEND=noninteractive \
    LC_ALL=C.UTF-8 \
    LANG=C.UTF-8

# Union of the per-path dependency sets in toolchain.json (kernel + rootfs/ISO).
# Keep in lockstep with setup-debian.sh and toolchain.json.
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates \
      build-essential make gcc \
      flex bison bc \
      libssl-dev libelf-dev libncurses-dev \
      cpio kmod zstd \
      debootstrap squashfs-tools xorriso \
      grub-pc-bin grub-efi-amd64-bin mtools dosfstools \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
CMD ["/bin/bash", "-lc", "echo 'Linux-shape toolchain ready. Run linux/build-shape.sh <kernel|rootfs|iso>.'"]
