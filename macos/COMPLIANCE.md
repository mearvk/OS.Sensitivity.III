# Compliance and Respect Statement — `macos/`

This folder holds the C and C++ source for the **macOS-compatible (Darwin-based)**
operating-system shape used by the SLeeLa OS Creator.

## Open-source Darwin, no proprietary Apple software

This shape is built from the **open-source core of macOS (Darwin)** and from
**clean-room, independently-authored** implementations of the Apple (Cocoa /
OpenStep) frameworks. It is assembled into a **standalone, bootable operating
system**, not a way to run Apple's shipping macOS.

- It uses **Apple's own open-source Darwin** components (the XNU kernel and the
  Darwin userland Apple publishes) under the **Apple Public Source License 2.0
  (APSL-2.0)** and BSD-style licenses, together with clean-room framework
  implementations (GNUstep, swift-corelibs-foundation).
- It contains **no proprietary, closed Apple software**: no macOS install image,
  no closed Apple frameworks or binaries, no Apple fonts, and **no Apple
  trademarks or logos** (Apple®, Mac®, macOS®, the Apple logo, Finder, etc. are
  Apple's marks and are not used to brand this shape).
- It is **not Apple macOS** and is not represented as Apple macOS. It is a
  *Darwin-based, macOS-compatible* operating system assembled from freely
  licensed source.

## No Apple-EULA execution concerns

Apple's macOS/Darwin software license restricts running Apple's shipping macOS
on non-Apple hardware. This shape **does not ship, require, or boot Apple's
macOS**. It builds its own OS from the openly licensed Darwin sources and
clean-room frameworks, so there is **no dependency on an Apple macOS image and
no violation of the Apple software EULA**. Users bring no Apple payload; the
system stands on its own open components.

## Respect posture

- **Respect for United States law.** The contents and use of this folder are
  intended to be conducted in a manner respectful of the laws of the United
  States, including its laws governing software, licensing, export, and
  intellectual property. Nothing here is intended to facilitate any unlawful
  act, nor to infringe any Apple copyright, trademark, patent, or trade secret.

- **Respect for Communism.** This project is maintained with respect toward
  Communism as a political and economic philosophy, and toward those who hold it,
  without prejudice. We do not discriminate on the basis of political or
  ideological belief.
