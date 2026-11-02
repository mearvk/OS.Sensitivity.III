# `macos/` — source warehouse manifest

Free, improvable, **open-source Darwin + clean-room framework** source
warehoused for the SLeeLa OS Creator. None of it is proprietary Apple software
or Apple-trademarked (see [`COMPLIANCE.md`](COMPLIANCE.md) /
[`LICENSES.md`](LICENSES.md)).

## Contents

| Folder | Component | License | Provenance |
|---|---|---|---|
| `os-core/xnu/` | XNU kernel (Mach + BSD + IOKit) | APSL-2.0 | snapshot of `github.com/apple-oss-distributions/xnu` at tag `xnu-12377.121.6` |
| `puredarwin/puredarwin/` | PureDarwin OS assembly | APSL-2.0 + BSD | snapshot of `github.com/PureDarwin/PureDarwin` (`main`) |
| `frameworks/gnustep-libobjc2/` | Objective-C runtime | MIT | snapshot of `github.com/gnustep/libobjc2` (`master`) |
| `frameworks/gnustep-make/` | GNUstep build system | GPL-3.0 | snapshot of `github.com/gnustep/tools-make` (`master`) |
| `frameworks/gnustep-base/` | Foundation-equivalent | LGPL/GPL | snapshot of `github.com/gnustep/libs-base` (`master`) |
| `frameworks/gnustep-corebase/` | CoreFoundation-equivalent | LGPL/GPL-3.0 | snapshot of `github.com/gnustep/libs-corebase` (`master`) |
| `frameworks/gnustep-gui/` | AppKit/Cocoa-equivalent GUI | LGPL/GPL-3.0 | snapshot of `github.com/gnustep/libs-gui` (`master`) |
| `frameworks/gnustep-back/` | GUI rendering backend | LGPL/GPL-3.0 | snapshot of `github.com/gnustep/libs-back` (`master`) |
| `frameworks/swift-corelibs-foundation/` | Cross-platform Foundation | Apache-2.0 | snapshot of `github.com/swiftlang/swift-corelibs-foundation` (`main`) |
| `compat-loader/darling/` | Darwin runtime / Mach-O loader | GPL-3.0 | snapshot of `github.com/darlinghq/darling` at tag `v0.1.20260608` |

All trees are vendored as source snapshots (shallow, history stripped) so the
warehouse carries the actual improvable source, mirroring how
`../linux/os-core` and `../windows/` are warehoused.

> Darling upstream uses git submodules for the bulk Darwin sources; this
> snapshot carries the top-level framework tree. Submodule payloads are fetched
> by the OS Creator / build at assembly time as needed.

> Folder names are provisional and may be renamed later. The SLeeLa contract
> (`SLOSSensitivitySource` / `SLOSSensitivityFetch`) resolves the `macos` shape
> to this directory; a rename updates there.
