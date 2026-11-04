# OS.Creator.md — integration specification for the SLeeLa OS Creator

> **Audience.** The **SLeeLa OS Creator** (`lib/os/os-creator/` in
> `mearvk/SLeeLa`) and its **OSCreator runnable**. This document describes — in
> principle — how the Creator connects to this warehouse, what it can expect to
> find, and how it turns a chosen **OS shape** into a real, bootable artifact
> for a user.
>
> This is a *contract* document: everything it references is machine-readable
> and on disk in this repository, and is kept honest by the validators in
> [`tools/`](tools/) running in CI. If this document and the descriptors ever
> disagree, **the descriptors win** — read them, do not hard-code.

---

## I. What this warehouse is

`OS.Sensitivity.III` is an OS-grade **C/C++ source** warehouse. It carries the
real, free, improvable source for three operating-system **shapes**, each in its
own top-level folder:

| Shape | Folder | Kind |
|---|---|---|
| `linux` | [`linux/`](linux/) | native Linux OS |
| `windows` | [`windows/`](windows/) | Windows-compatible (clean-room: Wine + ReactOS) |
| `macos` | [`macos/`](macos/) | macOS-compatible (Darwin-based: XNU + PureDarwin + GNUstep + Darling) |

The Creator does **not** need to know the folder layout in advance. It discovers
everything by resolving one descriptor.

---

## II. The single entry point: `shapes.json`

Start at [`shapes.json`](shapes.json) at the repo root. It is validated against
[`schemas/shapes-1.0.json`](schemas/shapes-1.0.json). Its `shapes` object maps a
**shape id** (`linux` / `windows` / `macos`) to a shape record. The keys the
Creator consumes:

| Key | Meaning |
|---|---|
| `root` | the shape's top-level folder |
| `status` | `populated` / `partial` / `planned` / `empty` |
| `components` or `paths` | the component source trees (each has a `path`, and usually `role`, `license`, `upstream`, `pinnedRef`, `buildEntry`) |
| `compliance`, `licenses`, `sources`, `build` | per-shape docs (provenance + how to build) |
| **`toolchain`** | → the shape's pinned build toolchain manifest (§IV) |
| **`orchestrator`** | → the shape's build orchestrator script (§IV) |
| **`provision`** | → the shape's first-boot provisioning descriptor (§V) |

All paths are **repo-relative** (POSIX, no leading `/`, no `..`). A shape is
resolvable only if every path it declares exists on disk — which the validators
guarantee.

### Minimal resolve algorithm

```
1. Read shapes.json; pick shapes[<shapeId>].
2. (Recommended) Run the validators (§VI) — fail closed if any path is missing.
3. Decide the BUILD MODEL (§III): baked artifact, or minimal-base + provision.
4a. BAKED:     provision shape.toolchain, then run shape.orchestrator -> artifact.
4b. PROVISION: ship a minimal base, inject shape.provision's first-boot hook.
5. Hand the resulting artifact / image to the user.
```

---

## III. Two build models

The Creator chooses **per shape, per user**:

- **Model A — baked artifact.** Provision the pinned toolchain, compile the
  component(s), and bake the result into the ISO/prefix. Fully self-contained
  and offline. Required for non-apt shapes (ReactOS, Darwin) and whenever the
  user needs an offline image.
- **Model B — minimal base + first-boot provisioning.** Ship a small base image
  and let it install the rest on first boot from the shape's `provision`
  descriptor. Lighter and faster per user; applies **only where a package
  manager applies** (apt on Linux and Wine-on-Linux). See the capability matrix
  in §VII.

Both models are first-class and are described by the descriptors below.

---

## IV. The toolchain contract (Model A)

`shapes[<id>].toolchain` points at a `toolchain.json`
(e.g. [`windows/toolchain/toolchain.json`](windows/toolchain/toolchain.json)),
validated against
[`schemas/toolchain-1.0.json`](schemas/toolchain-1.0.json). It tells the Creator
**exactly how to provision a hermetic build environment** and **what to run**:

| Key | Meaning for the Creator |
|---|---|
| `hostBase.image` | pinned base image (currently `ubuntu:24.04` for every shape, matching the Linux base this warehouse ships) |
| `hostBase.dockerfile` | build this for a hermetic environment: `docker build <dir>` |
| `hostBase.bareMetalSetup` | or run this to install the same pinned deps on a host: `sudo <script>` |
| `paths.<name>.component` | the source tree to build |
| `paths.<name>.buildEntry` | the upstream build command (declarative; the orchestrator wraps it) |
| `paths.<name>.requires.apt` | the exact package set the environment needs |
| `paths.<name>.artifact` | the declared output: `{ type: iso\|prefix\|image\|rootfs\|tarball, name }` |
| `orchestrator.script` | the one script the Creator actually calls |
| `orchestrator.usage` | its argument shape |

### Invoking the orchestrator

Every shape exposes `shape.orchestrator` (`<shape>/build-shape.sh`) with a
uniform contract: **pick a path, get its declared artifact.**

```sh
# Windows
windows/build-shape.sh wine    --arch amd64 --out OUT   # -> installed Wine prefix
windows/build-shape.sh reactos --arch amd64 --out OUT   # -> bootcd.iso

# macOS (cross-built on a Linux host)
macos/build-shape.sh darwin-os       --out OUT          # -> puredarwin.iso
macos/build-shape.sh frameworks      --out OUT          # -> GNUstep prefix
macos/build-shape.sh darwin-runtime  --out OUT          # -> Darling prefix

# Linux
linux/build-shape.sh kernel --out OUT                   # -> bzImage
linux/build-shape.sh rootfs --out OUT                   # -> staged Ubuntu rootfs
```

The orchestrator guards its prerequisites (it fails with a clear message if a
tool is missing) and prints `ARTIFACT: <what> -> <path>` on success. The Creator
should parse that line, or read `paths.<name>.artifact.name` and look in `--out`.

---

## V. The provisioning contract (Model B)

`shapes[<id>].provision` points at a `provision.json`
(e.g. [`linux/provision/provision.json`](linux/provision/provision.json)),
validated against
[`schemas/provision-1.0.json`](schemas/provision-1.0.json). It describes a
**minimal base + first-boot install**:

| Key | Meaning for the Creator |
|---|---|
| `status` | `active` (usable now) / `stub` (not applicable; see `description`) / `planned` |
| `base.kind` / `base.ref` / `base.source` | the minimal base to start from |
| `provisioner.type` | `apt` / `rapps` / `pkg` / `none` — **which package manager applies** |
| `provisioner.packages[]` | `{ name, version?, reason }` to install on first boot |
| `provisioner.reproducibility.snapshotMirror` | dated archive mirror for a deterministic boot |
| `provisioner.reproducibility.pinVersions` | when true, honor exact `pkg=version` pins |
| `provisioner.firstBoot[]` | ordered commands to run after install |
| `firstBootHook.mechanism` | `cloud-init` / `systemd-oneshot` / `none` |
| `firstBootHook.cloudInit` | inject this as the ISO's cloud-init user-data |
| `firstBootHook.unit` | or install this systemd one-shot unit |

For a `systemd-oneshot` hook, the shape also ships a `provision-run.sh` that
reads the `provision.json`, points apt at the pinned snapshot mirror, installs
the declared packages (honoring version pins), and runs the firstBoot commands
once. The Creator places `provision.json` where the unit expects it
(`/etc/sleela/provision.json`) and `provision-run.sh` at
`/usr/local/sbin/provision-run.sh`.

**If `provisioner.type` is not `apt`** (i.e. `rapps`, `pkg`, or `none`), Model B
does not apply to that path — the `description` explains why, and the Creator
must use Model A (bake the artifact). This is deliberate and honest: only
Debian/Ubuntu-family systems have apt.

---

## VI. Validators — trust, but verify

Before relying on any descriptor, the Creator (or CI) should run the
standard-library-only validators in [`tools/`](tools/) — they fail closed with
exact JSON locations:

| Validator | Guarantees |
|---|---|
| [`tools/validate_shapes.py`](tools/validate_shapes.py) | `shapes.json` conforms to its schema **and every declared path resolves on disk** |
| [`tools/validate_provenance.py`](tools/validate_provenance.py) | referenced docs exist/non-empty; `upstream` implies `license`; clean-room shapes carry `COMPLIANCE.md`+`LICENSES.md`; every `buildEntry` points at a real entry file |
| [`tools/validate_toolchain.py`](tools/validate_toolchain.py) | each `toolchain.json` conforms; its Dockerfile / setup script / orchestrator / components resolve; scripts are executable |
| [`tools/validate_provision.py`](tools/validate_provision.py) | each `provision.json` conforms; hook files resolve; a non-apt provisioner declares no apt packages; apt + `pinVersions` has a reproducibility pin |

All four run in CI ([`.github/workflows/validate-shapes.yml`](.github/workflows/validate-shapes.yml))
on every change to the contract.

---

## VII. Per-shape capability matrix (what the Creator can expect)

| Shape | Path | Model A artifact | Model B (first-boot) | Provisioner |
|---|---|---|---|---|
| `linux` | kernel / rootfs | `bzImage` / staged Ubuntu rootfs | **active** — base rootfs + apt on boot | `apt` |
| `windows` | wine | installed Wine prefix (WoW64) | **active** — apt on the Linux base | `apt` |
| `windows` | reactos | `bootcd.iso` | n/a (stub) — baked; apps via RAPPS | `rapps` |
| `macos` | darwin-os | `puredarwin.iso` | n/a (stub) — baked; Darwin pkg tooling | `pkg` |
| `macos` | frameworks / darwin-runtime | GNUstep / Darling prefix | n/a | `pkg` |

The single rule to remember: **apt is only the Linux provisioner** (and the
Wine path, because Wine runs on the Linux base). Everything else is baked.

---

## VIII. Compliance posture (non-negotiable)

The Creator must preserve the warehouse's compliance posture end to end
(see [`COMPLIANCE.md`](COMPLIANCE.md) and each shape's `COMPLIANCE.md`):

- **Windows is clean-room.** No Microsoft source, binaries, SDK, fonts, or
  license. Windows-*compatible* via Wine (clean-room Win32/Win64) and ReactOS.
  The Wine path ships **metric-compatible free fonts** (Liberation/Carlito/
  Caladea), never Microsoft fonts.
- **macOS is Darwin-based.** No Apple macOS image, Apple binaries, Apple SDK, or
  Apple developer account. XNU is public APSL-2.0 source; PureDarwin / GNUstep /
  Darling are openly licensed.
- Respectful of **United States law** (including others' intellectual property)
  and respectful of **Communism**.

The Creator should surface each shape's `license`/`compliance` data to the user
and must not substitute proprietary components for the clean-room ones.

---

## IX. Reproducibility notes (honest limits)

- The `toolchain.json` `hostBase.image` is pinned, and the `apt` package *names*
  are verified against the Ubuntu 24.04 (noble) archive. For a fully
  deterministic environment, pin exact apt **versions** and/or build against a
  dated snapshot mirror.
- The `provision.json` `snapshotMirror` values are currently placeholder dates;
  a real build should resolve them to an actual published
  `snapshot.ubuntu.com` timestamp so first boot is reproducible.
- macOS cross-builds Darwin on a Linux host; PureDarwin / Darling are actively
  developed — expect to track upstream for a fully polished desktop. For a
  pinned Darwin toolchain, PureDarwin ships a Nix `flake.nix`.
- A full ReactOS or Darwin build needs real time and disk (~15 GB); the Creator
  should budget accordingly and may cache the base toolchain image across users.

---

## X. Summary — the Creator's connection in one paragraph

Read [`shapes.json`](shapes.json); pick a shape; (optionally) run the four
validators to fail closed on any drift. For a **baked** build, provision the
shape's `toolchain` (Docker image or setup script) and call its `orchestrator`
with the chosen path to get a declared artifact. For a **first-boot** build on
an apt-capable path, ship a minimal base and inject the shape's `provision`
hook. Throughout, honor the clean-room / Darwin-based compliance posture and
surface provenance to the user. Everything the Creator needs is a
machine-readable descriptor in this repo — resolve it, don't assume it.
