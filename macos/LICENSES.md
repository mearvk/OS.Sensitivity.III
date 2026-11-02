# `macos/` — Licenses and Provenance

Every component warehoused under `macos/` is **freely licensed open source** —
either Apple's own open-source Darwin (APSL-2.0 / BSD) or clean-room,
independently-authored framework implementations. None of it is proprietary
Apple software, and none of it carries Apple trademarks.

| Component | Folder | Upstream | Pinned ref | License |
|---|---|---|---|---|
| **XNU kernel** — the macOS/iOS hybrid kernel (Mach + FreeBSD + IOKit), Apple open source | `os-core/xnu/` | `github.com/apple-oss-distributions/xnu` | `xnu-12377.121.6` | APSL-2.0 (see `APPLE_LICENSE`) |
| **PureDarwin** — community assembly of Apple's Darwin into a bootable OS | `puredarwin/puredarwin/` | `github.com/PureDarwin/PureDarwin` | `main` snapshot | APSL-2.0 + BSD (see its `APPLE_LICENSE.txt`) |
| **GNUstep libobjc2** — modern Objective-C runtime | `frameworks/gnustep-libobjc2/` | `github.com/gnustep/libobjc2` | `master` snapshot | MIT |
| **GNUstep make** — GNUstep build system | `frameworks/gnustep-make/` | `github.com/gnustep/tools-make` | `master` snapshot | GPL-3.0 |
| **GNUstep base** — Foundation-equivalent (non-GUI) frameworks | `frameworks/gnustep-base/` | `github.com/gnustep/libs-base` | `master` snapshot | LGPL (libs) / GPL (tools) |
| **GNUstep corebase** — CoreFoundation-equivalent | `frameworks/gnustep-corebase/` | `github.com/gnustep/libs-corebase` | `master` snapshot | LGPL / GPL-3.0 |
| **GNUstep gui** — AppKit/Cocoa-equivalent GUI | `frameworks/gnustep-gui/` | `github.com/gnustep/libs-gui` | `master` snapshot | LGPL (libs) / GPL-3.0 |
| **GNUstep back** — GUI rendering backend | `frameworks/gnustep-back/` | `github.com/gnustep/libs-back` | `master` snapshot | LGPL / GPL-3.0 |
| **swift-corelibs-foundation** — Apple's cross-platform Foundation | `frameworks/swift-corelibs-foundation/` | `github.com/swiftlang/swift-corelibs-foundation` | `main` snapshot | Apache-2.0 |
| **Darling** — Darwin runtime / Mach-O loader / dyld / launchd | `compat-loader/darling/` | `github.com/darlinghq/darling` | `v0.1.20260608` | GPL-3.0 |

## License notes

- **APSL-2.0** (XNU, Darwin userland) is an OSI-approved free-software license;
  it permits use, modification, and redistribution with source-disclosure
  obligations. Apple's macOS **EULA** is a separate thing and governs Apple's
  *shipping macOS image* — which this shape does **not** use. See
  [`COMPLIANCE.md`](COMPLIANCE.md).
- **GNUstep** libraries are LGPL (usable by any program, including non-free),
  while GNUstep applications/tools are GPL. Each subtree carries its own
  `COPYING` / `COPYING.LIB` / `COPYINGv3`.
- **Darling** and some GNUstep apps are **GPL-3.0** — strong copyleft; derivative
  distributions must honor GPL-3.0 source obligations.
- **libobjc2** is permissive **MIT**; **swift-corelibs-foundation** is
  **Apache-2.0**.
- Each vendored tree retains its upstream license file(s); this table is a
  summary, not a replacement for those files.

## Trademark note

Apple, Mac, macOS, Darwin, Finder, Cocoa, and the Apple logo are trademarks of
Apple Inc. They are referenced here only descriptively (to explain
compatibility) and are **not** used to brand or market this shape. This project
is independent and unaffiliated with Apple Inc.
