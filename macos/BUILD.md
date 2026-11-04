# Building the `macos/` shape

This document gives the exact steps to build the two cooperating paths of the
macOS-compatible (Darwin-based) shape. Everything here is built from the openly
licensed source warehoused under `macos/` — no Apple macOS image is required or
used (see [`COMPLIANCE.md`](COMPLIANCE.md)).

> Build these on a Linux host. The OS Creator drives the per-user assembly, but
> the steps below are the component build recipes it orchestrates.

---

## Path 0 — the OS Creator flow (toolchain + orchestrator)

When building **through the SLeeLa OS Creator**, you do not install build
dependencies by hand. The Creator provisions the pinned toolchain described by
[`toolchain/toolchain.json`](toolchain/toolchain.json) and invokes the
orchestrator [`build-shape.sh`](build-shape.sh).

```sh
# Provision the cross-build toolchain (Linux host):
docker build -t ossens-macos-toolchain macos/toolchain/      # hermetic
# or: sudo macos/toolchain/setup-debian.sh                    # bare metal

# Build an artifact:
macos/build-shape.sh darwin-os        # -> puredarwin.iso (bootable Darwin OS)
macos/build-shape.sh frameworks       # -> installed GNUstep prefix
macos/build-shape.sh darwin-runtime   # -> installed Darling prefix
```

No Apple macOS image, Apple binaries, Apple SDK, or Apple developer account is
used. For a fully pinned Darwin OS build, PureDarwin's `flake.nix` offers a Nix
toolchain (`nix build`). **Provisioning note:** Darwin is not apt-based, so the
"minimal base + first-boot apt" model does **not** apply here — the Darwin OS is
baked by `build-shape.sh darwin-os`; see
[`provision/provision.json`](provision/provision.json).

The sections below document the underlying component recipes the orchestrator
wraps, for building a single path by hand.

---

## Path 1 — the standalone Darwin OS (the built OS)

### 1a. XNU kernel — `os-core/xnu/`

XNU builds with Apple's build system plus the Darwin header/tool packages. On a
Linux cross-build host you need: `clang`/`llvm`, `cmake`, `ninja`, `bison`,
`flex`, `python3`, and the Darwin SDK headers that PureDarwin provides.

```sh
cd macos/os-core/xnu
# XNU expects companion packages (AvailabilityVersions, libdispatch, xnu headers,
# Libsystem) staged first; PureDarwin's build (Path 1b) wires these up for you.
# Standalone (Apple make-based) build of the kernel:
make SDKROOT=macosx ARCH_CONFIGS=X86_64 KERNEL_CONFIGS=RELEASE
# Output: BUILD/obj/.../mach_kernel (the Darwin kernel)
```

For a Linux-hosted cross build, prefer driving XNU **through PureDarwin** (next
section), which stages the required headers and tools in the right order.

### 1b. PureDarwin — `puredarwin/puredarwin/` (assembles the bootable OS)

PureDarwin uses CMake + Ninja to fetch/stage the Darwin userland and build a
bootable image around the XNU kernel.

```sh
cd macos/puredarwin/puredarwin
# Toolchain: clang/llvm, cmake>=3.20, ninja, git, python3, bison, flex,
#            uuid-dev, libssl-dev, and the cctools/ld64 + dyld toolchain.
cmake -G Ninja -B build -S . \
      -DCMAKE_BUILD_TYPE=Release
ninja -C build            # builds Darwin userland + kernel integration
ninja -C build image      # assembles the bootable PureDarwin image (ISO/dmg)
```

The result is a **standalone, bootable Darwin operating system** (kernel +
Darwin userland), testable under QEMU:

```sh
qemu-system-x86_64 -m 2048 -cdrom build/puredarwin.iso -boot d
```

> Nix users: `puredarwin/puredarwin/flake.nix` / `build.nix` provide a pinned,
> reproducible toolchain — `nix build` from that directory.

---

## Path 2 — frameworks + compatibility (the Mac API surface)

Build order matters: runtime -> make -> base -> corebase -> gui -> back.

### 2a. Objective-C runtime — `frameworks/gnustep-libobjc2/`

```sh
cd macos/frameworks/gnustep-libobjc2
cmake -B build -S . -DCMAKE_BUILD_TYPE=Release
cmake --build build && sudo cmake --install build
```

### 2b. GNUstep make — `frameworks/gnustep-make/`

```sh
cd macos/frameworks/gnustep-make
./configure && make && sudo make install
# then source the environment for subsequent builds:
. /usr/local/share/GNUstep/Makefiles/GNUstep.sh
```

### 2c. Foundation / CoreFoundation / AppKit — the GNUstep libraries

```sh
# Foundation-equivalent (non-GUI)
cd macos/frameworks/gnustep-base    && ./configure && make && sudo make install
# CoreFoundation-equivalent
cd macos/frameworks/gnustep-corebase && ./configure && make && sudo make install
# AppKit/Cocoa-equivalent GUI
cd macos/frameworks/gnustep-gui     && ./configure && make && sudo make install
# GUI rendering backend
cd macos/frameworks/gnustep-back    && ./configure && make && sudo make install
```

### 2d. swift-corelibs-foundation — `frameworks/swift-corelibs-foundation/`

Requires a Swift toolchain (`swift`/`swiftc`) + `cmake` + `ninja`.

```sh
cd macos/frameworks/swift-corelibs-foundation
cmake -G Ninja -B build -S . -DCMAKE_BUILD_TYPE=Release
ninja -C build
```

### 2e. Darling — `compat-loader/darling/` (Darwin runtime / Mach-O loader)

```sh
cd macos/compat-loader/darling
# Toolchain: clang/llvm, cmake, ninja, bison, flex, libfuse-dev, libudev-dev,
#            libxml2-dev, libcairo2-dev, libgl1-mesa-dev, libtiff-dev, and more
#            (see README.md). NOTE: upstream Darling uses git submodules for the
#            bulk Darwin sources; this warehouse carries the top-level tree.
#            Initialize submodules from upstream before a full build:
#   git submodule update --init --recursive   (against the upstream remote)
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -GNinja
ninja                      # builds the Darling runtime
sudo ninja install
```

---

## Putting it together

A technically complete macOS-compatible system is:

1. **Path 1** → a bootable Darwin OS (XNU kernel + PureDarwin userland).
2. **Path 2** → the Cocoa/Foundation/Objective-C framework surface and the
   Darling Darwin runtime, layered on top so the OS presents a Mac-style API and
   can run Darwin/Mach-O software.

The SLeeLa OS Creator orchestrates these recipes per user into a preliminary ISO
source build (see [`README.md`](README.md) §IV).

## Honest notes

- **XNU** is production Apple source (APSL-2.0); **PureDarwin** and the
  framework stack are actively developed and of varying completeness — expect to
  track upstream and apply fixes for a fully polished desktop.
- No step here requires an Apple macOS image, Apple binaries, or an Apple
  developer account. The whole system builds from the openly licensed source in
  this folder plus standard open build tools.
- The large libmalloc MallocBench trace fixtures were removed from this snapshot
  to keep the tree lean (see the `TRACES_REMOVED.md` note in that directory);
  they are benchmark input data and are not needed to build the OS.
