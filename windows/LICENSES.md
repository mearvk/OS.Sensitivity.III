# `windows/` — Licenses and Provenance

Every component warehoused under `windows/` is **clean-room, independently
authored open-source** software. None of it contains Microsoft source code,
Microsoft system binaries, or Microsoft trademarks. All of it is freely usable
and improvable under the licenses below.

| Component | Folder | Upstream | Pinned ref | License |
|---|---|---|---|---|
| **Wine** — clean-room Win32/Win64 API compatibility layer that runs unmodified Windows applications on a POSIX base. Written predominantly via black-box reverse engineering to avoid copyright issues. | `compat-loader/wine/` | `github.com/wine-mirror/wine` | `wine-10.20` | LGPL-2.1-or-later |
| **ReactOS** — from-scratch, clean-room operating system that is binary- and driver-compatible with the Microsoft Windows NT family. Contains no Microsoft code. | `reactos/reactos/` | `github.com/reactos/reactos` | `0.4.16-release` | GPL-2.0 |

## Notes

- **Wine** is distributed under the GNU LGPL (version 2.1 or later); see its
  own `COPYING.LIB` / `LICENSE` inside `compat-loader/wine/`.
- **ReactOS** is distributed under the GNU GPL version 2.0; see its own
  `COPYING` inside `reactos/reactos/`. Some ReactOS subcomponents carry
  compatible permissive licenses (BSD / MIT / LGPL) noted in their own headers.
- Both projects are developed as **clean-room** reimplementations: the goal is
  compatibility with the *published Windows interface*, achieved without copying
  Microsoft's implementation. This is what keeps the `windows/` shape lawful and
  ethical (see [`COMPLIANCE.md`](COMPLIANCE.md)).

## Companion technologies

Direct3D-to-Vulkan translation layers (DXVK, vkd3d-proton) and the Proton
distribution are themselves built on or alongside Wine and are open-source
(zlib / LGPL / Apache families). They may be warehoused later under
`compat-loader/` as the shape matures; they are likewise clean-room and carry no
Microsoft code.
