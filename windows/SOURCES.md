# `windows/` — source warehouse manifest

Free, improvable, **clean-room** Windows-compatibility source warehoused for the
SLeeLa OS Creator. None of it is Microsoft source or Microsoft binaries (see
[`COMPLIANCE.md`](COMPLIANCE.md) / [`LICENSES.md`](LICENSES.md)).

## Contents

| Folder | Component | License | Provenance |
|---|---|---|---|
| `compat-loader/wine/` | Wine — clean-room Win32/Win64 API compatibility layer (runs Windows apps on the Linux base) | LGPL-2.1-or-later | shallow snapshot of `github.com/wine-mirror/wine` at tag `wine-10.20` |
| `reactos/reactos/` | ReactOS — clean-room OS, binary/driver-compatible with Windows NT | GPL-2.0 | shallow snapshot of `github.com/reactos/reactos` at tag `0.4.16-release` |

Both trees are vendored as source snapshots (shallow, history stripped) so the
warehouse carries the actual improvable source, mirroring how `../linux/os-core`
and `../linux/os-loader` are warehoused.

> Folder names are provisional and may be renamed later. The SLeeLa contract
> (`SLOSSensitivitySource` / `SLOSSensitivityFetch`) resolves the `windows`
> shape to this directory; a rename updates there.
