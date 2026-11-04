# Building the `windows/` shape

Exact steps to build the two cooperating paths of the Windows-compatible shape.
Everything is built from the clean-room open source warehoused under `windows/`
— no Microsoft source, binaries, or SDK are required or used (see
[`COMPLIANCE.md`](COMPLIANCE.md)).

> Build on a Linux host. The OS Creator orchestrates these recipes per user; the
> steps below are the component build recipes.

---

## Path 0 — the OS Creator flow (toolchain + orchestrator)

When building **through the SLeeLa OS Creator**, you do not install build
dependencies by hand. The Creator provisions the **pinned toolchain** described
by [`toolchain/toolchain.json`](toolchain/toolchain.json) and then invokes the
orchestrator [`build-shape.sh`](build-shape.sh), which compiles a chosen path
into a declared artifact.

Provision the toolchain (pick one):

```sh
# Hermetic (recommended): a pinned ubuntu:24.04 image matching the linux/ base.
docker build -t ossens-windows-toolchain windows/toolchain/

# Or install the same pinned set on a Debian/Ubuntu host directly:
sudo windows/toolchain/setup-debian.sh
```

Then build an artifact:

```sh
# Wine compatibility layer -> an installed Wine prefix
windows/build-shape.sh wine    --arch amd64 --out out/wine

# ReactOS standalone OS -> bootcd.iso
windows/build-shape.sh reactos --arch amd64 --out out/reactos
```

On a Linux host the ReactOS path uses the **MinGW-w64 cross toolchain** (plus
CMake, Ninja, flex, bison) that the toolchain provides — **RosBE is not
required**; `build-shape.sh` exports the `ROS_ARCH` that `configure.sh` needs.
No Microsoft source, SDK, binaries, or license is used at any step.

The sections below document the underlying component recipes the orchestrator
wraps, for building a single path by hand.

---

## Path 0b — minimal base + first-boot provisioning (Wine path, lightweight)

Instead of baking a compiled artifact into the ISO, the OS Creator can ship a
**minimal Ubuntu base ISO** and provision the Wine layer **on first boot** from
a declarative config, [`provision/provision.json`](provision/provision.json)
(schema [`schemas/provision-1.0.json`](../schemas/provision-1.0.json)). This is
lighter and faster per user; the heavy install happens against the live apt
archive, pinned to a dated snapshot mirror for a reproducible first boot.

Two interchangeable first-boot mechanisms are provided:

- **cloud-init:** [`provision/user-data.yaml`](provision/user-data.yaml) — the
  Creator injects it as the ISO's user-data.
- **systemd one-shot:** [`provision/sleela-provision.service`](provision/sleela-provision.service)
  + [`provision/provision-run.sh`](provision/provision-run.sh), which reads
  `provision.json`, pins apt to the snapshot mirror, installs the declared
  packages (honoring exact version pins), and runs the firstBoot commands once.

The Wine path installs Wine plus **metric-compatible free fonts**
(Liberation/Carlito/Caladea) — **no Microsoft fonts are shipped**. The
**ReactOS path is not apt-provisionable** (it is a standalone OS with no apt);
that choice is recorded in
[`provision/reactos.provision.json`](provision/reactos.provision.json), and the
ReactOS ISO is produced by `build-shape.sh reactos` (baked), then apps are
added post-install via ReactOS's own RAPPS.

> Choose **Path 0b** for a small base + fast per-user provisioning where apt
> applies (Linux / Wine-on-Linux); choose **Path 0** (toolchain + orchestrator)
> when you need a fully baked, offline artifact or for the non-apt ReactOS path.

---

## Path 1 — Wine compatibility layer — `compat-loader/wine/`

Runs unmodified Windows `.exe` applications on top of the `../linux/` base.

```sh
cd windows/compat-loader/wine
# Toolchain (Debian/Ubuntu names): gcc, flex, bison, libx11-dev, libfreetype-dev,
#   libfontconfig-dev, libgl-dev, and (for 64+32 WoW64) gcc-multilib.
./configure --enable-win64            # or default for 32-bit, or --enable-archs for WoW64
make -j"$(nproc)"
sudo make install
# Verify:
wine --version                        # -> wine-10.20
```

A Windows program then runs with:

```sh
wine /path/to/program.exe
```

For Direct3D-heavy software, layer DXVK/vkd3d (companion projects) on top of
this Wine build; they are open source and may be warehoused under
`compat-loader/` later.

---

## Path 2 — ReactOS standalone OS — `reactos/reactos/`

Boots as its own Windows-NT-compatible operating system.

```sh
cd windows/reactos/reactos
# Toolchain: the ReactOS Build Environment (RosBE) is strongly recommended.
#   It provides a matched GCC/binutils + cmake + ninja. Alternatively MSVC 2010+.
./configure.sh            # (configure.cmd on Windows hosts) -> creates output dir
cd output-MinGW-amd64     # or output-MinGW-i386
ninja                     # build all modules
ninja bootcd              # -> bootcd.iso  (installable CD image)
ninja livecd              # -> livecd.iso  (bootable live image, optional)
```

Test under QEMU:

```sh
qemu-system-x86_64 -m 2048 -cdrom bootcd.iso -boot d
```

Install target note: ReactOS installs onto a FAT16/FAT32 active partition.

---

## Putting it together

- **Path 1 (Wine)** gives the fastest, highest-fidelity way to *run Windows
  applications* on the existing Linux base.
- **Path 2 (ReactOS)** gives a true standalone Windows-compatible OS.

The SLeeLa OS Creator assembles these per user into a preliminary ISO source
build (see [`README.md`](README.md)).

## Honest notes

- **Wine** app compatibility is high but not universal (some driver-level or
  anti-tamper software will not run). See the Wine AppDB / ProtonDB.
- **ReactOS 0.4.16** is of beta maturity.
- No step requires Microsoft source, binaries, a Windows license, or the
  Windows SDK — the whole system builds from the clean-room source in this
  folder plus standard open build tools.
