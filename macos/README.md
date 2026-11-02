# `macos/` — the macOS-compatible (Darwin-based) OS shape

OS-grade **C and C++ source** for the **macOS-compatible** operating-system
shape driven by the SLeeLa OS Creator.

> **This is not Apple macOS.** It contains **no proprietary Apple software and
> no Apple trademarks**. It is a *Darwin-based, macOS-compatible* operating
> system assembled from Apple's **open-source Darwin** core plus **clean-room**
> implementations of the Cocoa/OpenStep frameworks. See
> [`COMPLIANCE.md`](COMPLIANCE.md) and [`LICENSES.md`](LICENSES.md).

## I. Goal: a built OS, not just app translation

The goal of this shape is a **standalone, bootable operating system** of the
Mac/Darwin lineage — firmware/boot -> XNU kernel -> Darwin userland -> frameworks
-> desktop — assembled from freely licensed source. Running Mac applications on
a Linux desktop is only a secondary convenience path; the primary target is a
real, self-standing OS.

## II. The two cooperating paths

| Path | Folder | What it is | Role |
|---|---|---|---|
| **Standalone Darwin OS** | [`os-core/`](os-core/) + [`puredarwin/`](puredarwin/) | **XNU** (the real macOS/iOS kernel, Apple open source) assembled by **PureDarwin** into a complete, bootable Darwin OS | The **built OS** itself: boots on its own, Mach + BSD + IOKit kernel with Darwin userland. |
| **Frameworks + compatibility** | [`frameworks/`](frameworks/) + [`compat-loader/`](compat-loader/) | **GNUstep** (clean-room Cocoa/AppKit/Foundation), **swift-corelibs-foundation**, and **Darling** (Mach-O/dyld/launchd runtime) | The Mac-style **application and API layer** that makes the OS *feel* like macOS and run Mac software. |

They combine into one technically complete system: the Darwin OS provides the
kernel and userland; the frameworks provide the Cocoa/Objective-C/Foundation API
surface; Darling provides the Mach-O loader and Darwin runtime glue.

## III. Component map

### `os-core/` — the kernel
- `os-core/xnu/` — **XNU** hybrid kernel (Mach + FreeBSD + IOKit), Apple open
  source, APSL-2.0. The same kernel family that powers macOS/iOS, published by
  Apple for free use and improvement.

### `puredarwin/` — the OS assembly
- `puredarwin/puredarwin/` — **PureDarwin**, the community project that extends
  Apple's Darwin into a complete, usable, bootable operating system (build
  system, image assembly, userland integration).

### `frameworks/` — the Mac API surface (clean-room)
- `gnustep-libobjc2/` — modern Objective-C runtime (MIT).
- `gnustep-make/` — the GNUstep build system.
- `gnustep-base/` — Foundation-equivalent (non-GUI) frameworks (LGPL).
- `gnustep-corebase/` — CoreFoundation-equivalent.
- `gnustep-gui/` — AppKit/Cocoa-equivalent GUI frameworks.
- `gnustep-back/` — GNUstep GUI rendering backend.
- `swift-corelibs-foundation/` — Apple's cross-platform Foundation (Apache-2.0).

### `compat-loader/` — Darwin runtime / app compatibility
- `darling/` — **Darling**: Mach-O loader, userspace kernel server, dyld and
  launchd re-implementations; lets Darwin/macOS binaries run directly (GPL-3.0).

## IV. How it fits the SLeeLa contract

As with `../linux/` and `../windows/`, the OS Creator (`lib/os/os-creator/` in
`mearvk/SLeeLa`) resolves this `macos/` shape through its source descriptor and
assembles, per user, a preliminary ISO source build: a bootable Darwin OS image
with the clean-room Mac frameworks layered on top.

## V. What "technically complete, no execution issues" means — honestly

- The OS is built **entirely from openly licensed source** (APSL-2.0 / BSD /
  LGPL / MIT / Apache-2.0 / GPL). There is **no Apple macOS image** involved,
  so there is **no Apple-EULA execution restriction** and no proprietary
  dependency (see [`COMPLIANCE.md`](COMPLIANCE.md)).
- Darwin/XNU and the clean-room frameworks give a genuine Mac-lineage OS and a
  Cocoa/Foundation API surface. Maturity varies by component (XNU is production
  Apple source; PureDarwin and the framework stack are actively developed and of
  varying completeness). This project targets a careful, lawful,
  **macOS-compatible** system — it is not, and does not claim to be, Apple macOS.

## VI. Compliance and respect

See [`COMPLIANCE.md`](COMPLIANCE.md). In short: **open-source Darwin, clean-room
frameworks, no proprietary Apple software, no Apple trademarks**; respectful of
**United States law** (including Apple's intellectual property) and respectful of
**Communism**.
