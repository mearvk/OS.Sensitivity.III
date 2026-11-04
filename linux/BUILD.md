# Building the `linux/` shape

Exact steps to build the native Linux OS shape from the source warehoused under
`linux/`. This shape is the base the `../windows/` (Wine) and `../macos/`
(Darling) compatibility paths layer on top of.

> Build on a Linux host (x86_64). The SLeeLa OS Creator orchestrates these
> recipes per user into a preliminary ISO source build; the steps below are the
> component build recipes it drives, in boot order:
> firmware/boot -> kernel -> base userland -> filesystem -> desktop -> edition.

---

## Path 0 — the OS Creator flow (toolchain + orchestrator + provisioning)

When building **through the SLeeLa OS Creator**, the Creator provisions the
pinned toolchain ([`toolchain/toolchain.json`](toolchain/toolchain.json)) and
invokes the orchestrator [`build-shape.sh`](build-shape.sh).

```sh
# Provision the toolchain (Linux x86_64 host):
docker build -t ossens-linux-toolchain linux/toolchain/     # hermetic
# or: sudo linux/toolchain/setup-debian.sh                   # bare metal

# Build artifacts (boot order):
linux/build-shape.sh kernel    # -> bzImage (compiled Linux 5.10.254 kernel)
linux/build-shape.sh rootfs    # -> staged Ubuntu 24.04.4 root filesystem
```

**This shape is the native apt case.** Instead of apt-installing the kernel,
systemd, GRUB, and desktop into a chroot at build time (section 2 below), the
Creator can ship the **minimal base rootfs** and defer that install to **first
boot** via [`provision/provision.json`](provision/provision.json) (cloud-init
[`user-data.yaml`](provision/user-data.yaml) or the systemd one-shot
[`sleela-provision.service`](provision/sleela-provision.service) +
[`provision-run.sh`](provision/provision-run.sh)), pinned to a dated snapshot
mirror for a reproducible boot.

The sections below document the component recipes the orchestrator wraps.

---

## 1. Kernel — `os-core/linux-5.10.254/`

A complete, buildable Linux 5.10.254 tree.

```sh
cd linux/os-core/linux-5.10.254
# Toolchain (Debian/Ubuntu): build-essential, flex, bison, bc, libssl-dev,
#   libelf-dev, libncurses-dev, cpio, kmod, and (for initramfs) zstd.
make defconfig                 # or: make x86_64_defconfig
make -j"$(nproc)"              # builds arch/x86/boot/bzImage
make -j"$(nproc)" modules
make INSTALL_MOD_PATH=../_modroot modules_install
# Kernel image: arch/x86/boot/bzImage
```

> Note: building the kernel requires the host `flex` tool; `make defconfig`
> exercises the full kbuild machinery (scripts/basic/fixdep, kconfig) and then
> compiles the configured tree.

---

## 2. Base userland — `os-loader/userland/`

The lean free userland plus a real Ubuntu 24.04.4 base root filesystem. The base
rootfs already contains a dpkg-managed system (bash, dpkg, the full apt suite,
debconf, systemd libraries), so the OS is assembled by extracting the rootfs and
apt-installing the remaining packages (kernel image, full systemd, GRUB) into it.

```sh
cd linux/os-loader/userland
# Stage a build root from the base rootfs:
mkdir -p ../_rootfs
sudo tar -xzf ubuntu-base-24.04.4-base-amd64.tar.gz -C ../_rootfs
# Bind-mount and enter to complete the system inside the root:
sudo cp /etc/resolv.conf ../_rootfs/etc/
sudo chroot ../_rootfs /bin/bash -c '
  apt-get update &&
  apt-get install -y --no-install-recommends \
      linux-image-generic systemd-sysv grub-pc-bin grub-efi-amd64-bin \
      initramfs-tools '
# The lean free pieces (openjdk, x11, drm, gui glue) build per their own
# Makefiles; see os-loader/userland/Makefile.
make -C . 2>/dev/null || true
```

> The base rootfs is a plain tarball tracked in git (no Git LFS); a normal clone
> retrieves it in full.

---

## 3. Filesystem module — `file-systems/tac3/`

The in-tree TAC3 filesystem builds as an external kernel module against the
kernel from step 1.

```sh
cd linux/file-systems/tac3
make -C ../../os-core/linux-5.10.254 M="$(pwd)" modules
# Produces tac3.ko; also has a userspace diagnostic (tac3ctl, see installer/).
```

---

## 4. Desktop — `desktop-loader/gnome-source/`

GNOME component source, built module by module via the provided helper.

```sh
cd linux/desktop-loader/gnome-source
# Toolchain: meson, ninja, gcc, plus each module's -dev dependencies
#   (glib, gtk, pango, cairo, gdk-pixbuf, mutter, gnome-shell, ...).
./build-module.sh glib
./build-module.sh gtk
./build-module.sh mutter
./build-module.sh gnome-shell
# ... see gnome-source/README.md / SECTIONAL.md for the full module order.
```

---

## 5. Edition theme — `ubuntu-white/`

The Ubuntu White edition is a theme/branding layer (GTK CSS, SVG icons, font
preference) applied over the GNOME desktop.

```sh
cd linux/ubuntu-white
# Follows the inspect -> plan -> authorize -> apply -> verify model (INSTALL.md):
#   - GTK theme CSS         -> gtk.css
#   - SVG icon theme assets -> icons/
#   - desktop font preference
# Applied by the installer (step 6) into the target root.
```

---

## 6. Installer — `installer/`

The manifest-driven installer assembles and installs the native tool set and the
edition into a target root.

```sh
cd linux/installer
./build.sh                 # build the native tool set (package-installer, etc.)
./install-all.sh           # manifest-driven install (installer/install-manifest.txt)
# installer/linux/Makefile builds the C tools (package_installer, nxtt, ...).
```

---

## Putting it together (ISO source build)

1. Build the **kernel** (1) and the **tac3** module (3).
2. Stage the **base rootfs** and complete it with apt (2) — this yields a
   bootable, dpkg/apt/systemd-managed Ubuntu-lineage system.
3. Build and install the **GNOME desktop** (4) and apply the **ubuntu-white**
   edition (5) via the **installer** (6).
4. Produce a bootable image (GRUB + initramfs are installed in step 2); test
   under QEMU:

```sh
qemu-system-x86_64 -m 4096 -drive file=disk.img,format=raw -boot c
```

## Honest notes

- The kernel is the complete upstream 5.10.254 tree and builds with a standard
  host toolchain; no proprietary pieces are involved.
- The base rootfs is a genuine Ubuntu 24.04.4 base system; completing it with
  `apt-get install` requires network access to the Ubuntu archive at build time.
- Large bundled vendor applications (chromium, vendor JDKs, wine) were
  intentionally excluded from `os-loader/` (see `os-loader/SOURCES.md`); they are
  not required to boot the base OS and are provided by the compatibility shapes
  or installed via apt as desired.
```
