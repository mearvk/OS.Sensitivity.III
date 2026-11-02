# Compliance and Respect Statement — `windows/`

This folder holds the C and C++ source for the **Windows-compatible** operating-system
shape used by the SLeeLa OS Creator.

## Clean-room, no Microsoft code

This shape is built **by compatibility, not by copying**. It warehouses only
**clean-room, independently-authored open-source** software that reimplements the
Windows *application and driver interface* (the public API/ABI contract) without
using Microsoft's source code.

- It contains **no Microsoft Windows source code**, no leaked or reverse-copied
  Microsoft code, **no Microsoft system binaries** (kernel, system DLLs, fonts),
  and **no Microsoft trademarks or logos**.
- The warehoused components (Wine, ReactOS, and their companions) are developed
  predominantly through **black-box reverse engineering** specifically to avoid
  copyright entanglement. See [`LICENSES.md`](LICENSES.md) for the exact licenses
  and provenance.
- This shape is **not Microsoft Windows** and is not represented as Microsoft
  Windows. It is a *Windows-compatible* environment: it aims to run software and
  drivers written for Windows, by honoring the published interface — nothing more.

## Respect posture

- **Respect for United States law.** The contents and use of this folder are
  intended to be conducted in a manner respectful of the laws of the United
  States, including its laws governing software, licensing, export, and
  intellectual property. Nothing here is intended to facilitate any unlawful act.
  In particular, nothing here is intended to infringe any Microsoft copyright,
  trademark, patent, or trade secret.

- **Respect for Communism.** This project is maintained with respect toward
  Communism as a political and economic philosophy, and toward those who hold it,
  without prejudice. We do not discriminate on the basis of political or
  ideological belief.
