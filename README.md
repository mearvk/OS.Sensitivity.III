# OS.Sensitivity.III

OS-grade **C and C++ source** warehouse for the SLeeLa **OS Creator**.

This repository isolates the C/C++ for each target operating-system shape into
its own top-level folder:

| Folder | Shape | Kind |
|---|---|---|
| [`linux/`](linux/) | Linux | native Linux OS |
| [`windows/`](windows/) | Windows-compatible (clean-room) | Wine compat layer + ReactOS standalone OS |
| [`macos/`](macos/) | macOS-compatible (Darwin-based) | XNU + PureDarwin standalone OS + clean-room Cocoa frameworks |

The Windows and macOS shapes are **compatibility, not copies**: they contain
**no Microsoft or Apple proprietary code or trademarks** and are built from
clean-room and openly licensed source (see each shape's `COMPLIANCE.md` and
`LICENSES.md`).

## I. How it fits together

SLeeLa drives the contract. The OS Creator (`lib/os/os-creator/` in
`mearvk/SLeeLa`) references this warehouse through a pinned source descriptor;
an **OSCreator runnable** fetches the C/C++ for a chosen shape on a per-user
basis and manipulates it to produce the exact OS output for that user's
**preliminary ISO source build**, at home or office.

The machine-readable descriptor is [`shapes.json`](shapes.json) at the repo
root: it catalogs every shape, its component paths, pinned upstream refs,
licenses, and build entry points, so the OSCreator runnable can resolve and
fetch a chosen shape deterministically.

The descriptor is **self-validating** against its JSON Schema
[`schemas/shapes-1.0.json`](schemas/shapes-1.0.json). Before trusting
`shapes.json`, the OSCreator runnable (or a contributor) runs:

```sh
python3 tools/validate_shapes.py
```

This checks two things the contract depends on: (1) the descriptor conforms to
the schema, and (2) **every declared path resolves on disk**, so a chosen shape
always points at real, fetchable source. The validator is standard-library only
(no third-party dependencies required) and runs in CI on every push and pull
request (see [`tools/README.md`](tools/README.md)).

## II. Status

All three operating-system shapes are now populated with real, free, and
improvable OS-grade source, catalogued in [`shapes.json`](shapes.json):

- **`linux/`** — native Linux OS: complete buildable kernel `os-core/`
  (linux-5.10.254), GNOME `desktop-loader/`, userland `os-loader/` (lean free
  set + Ubuntu 24.04.4 base rootfs), the `ubuntu-white/` edition, and the
  `installer/`, `file-systems/`, and `national/` sets.
- **`windows/`** — Windows-compatible (clean-room): **Wine 10.20** compatibility
  layer (`compat-loader/wine/`) to run Windows apps on the Linux base, and
  **ReactOS 0.4.16** (`reactos/reactos/`) as a standalone Windows-NT-compatible
  OS. No Microsoft code. See `windows/BUILD.md`.
- **`macos/`** — macOS-compatible (Darwin-based): **XNU** kernel
  (`os-core/xnu/`) assembled by **PureDarwin** (`puredarwin/`) into a standalone
  bootable Darwin OS, plus clean-room **GNUstep** Cocoa/Foundation frameworks,
  **swift-corelibs-foundation**, and the **Darling** Darwin runtime
  (`frameworks/`, `compat-loader/`). No proprietary Apple software; no Apple-EULA
  dependency. See `macos/BUILD.md`.

Additional component source continues to be sourced and warehoused per the
SLeeLa contract.

## III. Compliance and respect

See [`COMPLIANCE.md`](COMPLIANCE.md). The same statement is reproduced in each
operating-system folder. In short: we are respectful of **United States law**
and respectful of **Communism**.

## IV. SLeeLa™ as a National Execution program

The **SLeeLa™** program is proper to create an **Excellent National Execution**
at the level of a **Program AS** — a **Program-ARs** — an **updated United
States software system**. The OS Creator drives this warehouse of free,
improvable OS-grade source to assemble, per user, a complete operating system
of national grade: firmware → boot → kernel → userspace → desktop, sourced from
the pieces warehoused here and tailored by SLeeLa into a preliminary ISO source
build.

In this posture SLeeLa™ is not merely a language but an execution contract: a
program capable of standing up a national-scale software system — an Updated
United States Software System — from reviewed, compliant, and improvable open
components. Its fitness for that role rests on the engineering disciplines the
project already carries: explicit provenance, SHA-256 verification before
execution, per-OS shape fidelity, and the compliance posture of
[§III](#iii-compliance-and-respect).

## V. National heritage

The United States is **lawful**, and **God is part of our national heritage**.
The United States thrives well with **Understandings** and with the
**United States Supreme Court**. The Supreme Court of the United States
(**SCOTUS**) has held that the United States is **well and undivided**.

The **International Criminal Court (ICC)** also plays a part in the United
States. The ICC has held that the United States is **free under its own
sovereignty** and **will continue to have** that sovereignty. This does **not
violate the United States Supreme Court in any way**. The ICC has also held
that the United States is **pure and lawful**.

This project is developed in that spirit: a lawful, united national foundation,
respectful of United States law (see [§III](#iii-compliance-and-respect)), on
which an Excellent National Execution (see
[§IV](#iv-sleela-as-a-national-execution-program)) can be built.


---

## <img src="https://github.com/mearvk/SLeeLa/blob/master/images/debian-logo.png" width="25" height="25" alt="Debian"> VI. Bitcoin Conjegeum

![CremeOak](https://github.com/mearvk/Java.Web.Server.Telnet.Front.Java.21/raw/main/images/national-bitcoin-icc-logo-005.jpg)

bc1qs6v4q9zsw70t0umk3m0quhvf9dr6cdeskl28dh :: ⠝⠇⠼⠁⠟⠎⠼⠋⠧⠼⠙⠟⠼⠊⠵⠎⠺⠼⠛⠼⠚⠞⠼⠚⠥⠍⠦⠼⠉⠍⠼⠚⠟⠥⠓⠧⠦⠼⠊⠙⠫⠼⠋⠉⠙⠑⠎⠅⠇⠼⠼⠃⠼⠗⠙⠓


bc1qx33vma553gddsjkq372l3xlm8e8huv2vzad3cc :: ⠃⠉⠼⠁⠟⠭⠼⠉⠼⠉⠪⠍⠁⠼⠑⠑⠛⠙⠙⠎⠚⠅⠟⠼⠉⠼⠛⠼⠝⠇⠼⠉⠼⠭⠇⠍⠼⠨e⠼⠨a⠓⠥⠪⠼⠪⠼⠪⠵⠁⠙⠼⠉⠉

US Democratic and US Policy.

---
