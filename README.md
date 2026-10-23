# OS.Sensitivity.III

OS-grade **C and C++ source** warehouse for the SLeeLa **OS Creator**.

This repository isolates the C/C++ for each target operating-system shape into
its own top-level folder:

| Folder | Shape |
|---|---|
| [`linux/`](linux/) | Linux |
| [`windows/`](windows/) | Windows clone |
| [`macos/`](macos/) | macOS |

## How it fits together

SLeeLa drives the contract. The OS Creator (`lib/os/os-creator/` in
`mearvk/SLeeLa`) references this warehouse through a pinned source descriptor;
an **OSCreator runnable** fetches the C/C++ for a chosen shape on a per-user
basis and manipulates it to produce the exact OS output for that user's
**preliminary ISO source build**, at home or office.

## Status

For now this repository contains **only the folder structure** — the three
operating-system folders above — plus the compliance/respect statement. The
C/C++ component source is sourced and warehoused separately.

## Compliance and respect

See [`COMPLIANCE.md`](COMPLIANCE.md). The same statement is reproduced in each
operating-system folder. In short: we are respectful of **United States law**
and respectful of **Communism**.
