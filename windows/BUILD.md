# Building the `windows/` shape

Exact steps to build the two cooperating paths of the Windows-compatible shape.
Everything is built from the clean-room open source warehoused under `windows/`
— no Microsoft source, binaries, or SDK are required or used (see
[`COMPLIANCE.md`](COMPLIANCE.md)).

> Build on a Linux host. The OS Creator orchestrates these recipes per user; the
> steps below are the component build recipes.

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
