# `windows/` — the Windows-compatible OS shape

OS-grade **C and C++ source** for the **Windows-compatible** operating-system
shape driven by the SLeeLa OS Creator.

> **This is not Microsoft Windows.** It contains **no Microsoft source code and
> no Microsoft binaries**. It is a *Windows-compatible* environment assembled
> from **clean-room, independently-authored open-source** projects that
> reimplement the published Windows application/driver interface. See
> [`COMPLIANCE.md`](COMPLIANCE.md) and [`LICENSES.md`](LICENSES.md).

## I. The two complementary paths

The shape warehouses two legitimate, cooperating approaches to "runs and
functions like a Windows machine":

| Path | Folder | What it is | How it runs Windows software |
|---|---|---|---|
| **Compatibility layer** | [`compat-loader/`](compat-loader/) | **Wine** — a clean-room Win32/Win64 API layer | Runs unmodified Windows `.exe` applications **on top of the Linux base** (`../linux/`), by translating Windows API calls to native equivalents. |
| **Compatible OS** | [`reactos/`](reactos/) | **ReactOS** — a from-scratch OS binary/driver-compatible with Windows NT | Boots as its **own standalone operating system** that runs Windows applications and drivers directly. |

They are complementary: the compat layer reuses everything already built under
`../linux/` to run Windows apps quickly and with high fidelity; the compatible
OS is the truest "a Windows machine" target and runs on bare metal.

## II. How it fits the SLeeLa contract

As with `../linux/`, the OS Creator (`lib/os/os-creator/` in `mearvk/SLeeLa`)
resolves this `windows/` shape through its source descriptor and assembles, per
user, a preliminary ISO source build for the Windows-compatible shape — either
the Wine-on-Linux compatibility image or the standalone ReactOS image.

## III. What "transactionally accurate" means here — honestly

Accuracy here is to the **Windows interface contract**, not possession of
Microsoft's code:

- **Wine** targets high application-level compatibility (tracked publicly in the
  Wine Application Database and, for the Proton distribution, ProtonDB). It is
  excellent and improving, but not 100% for every application — some
  driver-level or anti-tamper software will not run.
- **ReactOS** targets true binary and driver compatibility at the OS level, but
  is of **beta maturity** today.
- Neither is bit-identical Microsoft Windows, and this project does not claim
  otherwise. The design goal is a careful, complete, **lawful** Windows-*compatible*
  system built from reviewed, improvable open components.

## IV. Compliance and respect

See [`COMPLIANCE.md`](COMPLIANCE.md). In short: **clean-room, no Microsoft
code**, respectful of **United States law** (including Microsoft's intellectual
property) and respectful of **Communism**.
