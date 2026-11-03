#!/usr/bin/env python3
"""Provenance / compliance and build-entry smoke checks for OS.Sensitivity.III.

This complements tools/validate_shapes.py (which checks descriptor structure
and that declared paths resolve). Here we assert the *content contract* the
SLeeLa OS Creator relies on once a shape has been resolved:

  LAYER 1 -- PROVENANCE / COMPLIANCE
    * Each shape referenced doc (compliance / licenses / sources / build) and
      each component `sources` file exists and is non-empty.
    * Every component that declares an `upstream` also declares a `license`
      (and vice-versa is reported as a soft warning), so provenance is never
      half-stated.
    * Each component's SOURCES.md, when present, actually mentions the
      component's pinned ref / upstream (a light provenance cross-check, not a
      full parse).
    * Clean-room shapes (notMicrosoftWindows / notAppleMacOS) carry a
      LICENSES.md and a COMPLIANCE.md -- the compliance posture must be stated.

  LAYER 2 -- BUILD-ENTRY SMOKE
    * For every component that declares a `buildEntry`, the file(s) that entry
      point needs are present at the component `path` (e.g. `make` -> Makefile,
      `./configure` -> configure, `cmake` -> CMakeLists.txt). This is a static
      smoke test: it does not run any build, it just proves the entry point is
      not dangling.

Standard-library only. No network. Exit status 0 only when both layers pass;
warnings never fail the run but are printed.

Usage:
    python3 tools/validate_provenance.py
    python3 tools/validate_provenance.py --shapes shapes.json --root .
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Map a build-entry token to the files (any-of groups) it requires at the
# component path. Each entry is a list of "requirement groups"; a group is a
# tuple of acceptable filenames (any one satisfies the group).
BUILD_ENTRY_REQUIREMENTS: dict[str, list[tuple[str, ...]]] = {
    "make": [("Makefile", "makefile", "GNUmakefile")],
    "./configure": [("configure", "configure.ac", "configure.in")],
    "./configure.sh": [("configure.sh",)],
    "cmake": [("CMakeLists.txt",)],
    "ninja": [("CMakeLists.txt", "build.ninja", "configure.sh")],
    "meson": [("meson.build",)],
    "./autogen.sh": [("autogen.sh",)],
}

# Tokens that CONFIGURE a tree and generate the build files consumed by a later
# step. When one of these appears (and is itself satisfied) earlier in the same
# buildEntry, a following `make`/`ninja` is satisfied by the generated output
# and we do not require the generated Makefile/build.ninja to pre-exist.
CONFIGURE_PRECURSORS = {"./configure", "./configure.sh", "cmake", "meson", "./autogen.sh"}
GENERATED_BY_CONFIGURE = {"make", "ninja"}


class Finding:
    __slots__ = ("level", "where", "msg")

    def __init__(self, level: str, where: str, msg: str) -> None:
        self.level = level  # "error" | "warn"
        self.where = where
        self.msg = msg

    def __str__(self) -> str:
        tag = "WARN " if self.level == "warn" else "ERROR"
        return f"  {tag} [{self.where}] {self.msg}"


def iter_components(data):
    """Yield (shape_name, shape, bag_name, comp_name, comp) for every component."""
    for sname, shape in (data.get("shapes") or {}).items():
        if not isinstance(shape, dict):
            continue
        for bag in ("components", "paths"):
            for cname, comp in (shape.get(bag) or {}).items():
                if isinstance(comp, dict):
                    yield sname, shape, bag, cname, comp


def _exists_nonempty(repo_root: Path, rel: str) -> tuple[bool, bool]:
    """Return (exists, nonempty)."""
    p = (repo_root / rel)
    if not p.exists() or not p.is_file():
        return (False, False)
    try:
        return (True, p.stat().st_size > 0)
    except OSError:
        return (True, False)


# --------------------------------------------------------------------------- #
# Layer 1: provenance / compliance                                            #
# --------------------------------------------------------------------------- #
def check_provenance(data, repo_root: Path) -> list[Finding]:
    out: list[Finding] = []

    # Shape-level docs must exist and be non-empty.
    for sname, shape in (data.get("shapes") or {}).items():
        if not isinstance(shape, dict):
            continue
        w = f"shapes/{sname}"
        for key in ("compliance", "licenses", "sources", "build"):
            rel = shape.get(key)
            if not isinstance(rel, str):
                continue
            exists, nonempty = _exists_nonempty(repo_root, rel)
            if not exists:
                out.append(Finding("error", f"{w}/{key}", f"referenced doc missing: {rel!r}"))
            elif not nonempty:
                out.append(Finding("error", f"{w}/{key}", f"referenced doc is empty: {rel!r}"))

        # Clean-room shapes must state their compliance posture.
        if shape.get("notMicrosoftWindows") or shape.get("notAppleMacOS") or shape.get("cleanRoom"):
            for required in ("compliance", "licenses"):
                if not isinstance(shape.get(required), str):
                    out.append(
                        Finding(
                            "error",
                            w,
                            f"clean-room shape must declare '{required}' (compliance posture)",
                        )
                    )

    # Component-level provenance.
    for sname, shape, bag, cname, comp in iter_components(data):
        cw = f"shapes/{sname}/{bag}/{cname}"

        src = comp.get("sources")
        if isinstance(src, str):
            exists, nonempty = _exists_nonempty(repo_root, src)
            if not exists:
                out.append(Finding("error", f"{cw}/sources", f"SOURCES doc missing: {src!r}"))
            elif not nonempty:
                out.append(Finding("error", f"{cw}/sources", f"SOURCES doc is empty: {src!r}"))
            else:
                # Light provenance cross-check: does the doc mention the ref/upstream?
                text = (repo_root / src).read_text(encoding="utf-8", errors="replace")
                low = text.lower()
                ref = comp.get("pinnedRef")
                up = comp.get("upstream")
                mentioned = False
                for token in (ref, up):
                    if isinstance(token, str) and token:
                        # match the last path segment of an upstream, or the ref verbatim
                        needle = token.split("/")[-1].lower()
                        if needle and needle in low:
                            mentioned = True
                            break
                if (ref or up) and not mentioned:
                    out.append(
                        Finding(
                            "warn",
                            f"{cw}/sources",
                            f"SOURCES doc does not mention pinned ref/upstream "
                            f"({comp.get('pinnedRef') or comp.get('upstream')!r})",
                        )
                    )

        # Provenance must not be half-stated: upstream without a license.
        has_upstream = isinstance(comp.get("upstream"), str) and comp["upstream"]
        has_license = isinstance(comp.get("license"), str) and comp["license"]
        if has_upstream and not has_license:
            out.append(
                Finding("error", cw, "declares 'upstream' but no 'license' (provenance half-stated)")
            )
        if has_license and not has_upstream and bag == "paths":
            # In the windows/macos `paths` bags upstream is the norm; flag softly.
            out.append(Finding("warn", cw, "declares 'license' but no 'upstream'"))

        # Framework member sub-table: each member should carry upstream + license.
        members = comp.get("members")
        if isinstance(members, dict):
            for mname, m in members.items():
                mw = f"{cw}/members/{mname}"
                if not (isinstance(m, dict) and m.get("upstream")):
                    out.append(Finding("warn", mw, "member missing 'upstream'"))
                if not (isinstance(m, dict) and m.get("license")):
                    out.append(Finding("warn", mw, "member missing 'license'"))

    return out


# --------------------------------------------------------------------------- #
# Layer 2: build-entry smoke                                                  #
# --------------------------------------------------------------------------- #
def _build_tokens(entry: str) -> list[str]:
    """Extract recognizable build tokens from a buildEntry string."""
    # Split on shell separators, keep leading command of each clause.
    clauses = re.split(r"&&|\|\||;", entry)
    tokens: list[str] = []
    for clause in clauses:
        clause = clause.strip()
        if not clause:
            continue
        head = clause.split()[0]
        tokens.append(head)
    return tokens


def check_build_entries(data, repo_root: Path) -> list[Finding]:
    out: list[Finding] = []
    checked = 0
    for sname, shape, bag, cname, comp in iter_components(data):
        entry = comp.get("buildEntry")
        path = comp.get("path")
        if not isinstance(entry, str) or not isinstance(path, str):
            continue
        comp_dir = repo_root / path
        cw = f"shapes/{sname}/{bag}/{cname}"
        if not comp_dir.is_dir():
            out.append(Finding("error", cw, f"buildEntry component path is not a directory: {path!r}"))
            continue

        recognized_any = False
        configure_satisfied = False  # a configure precursor earlier in this entry passed
        for token in _build_tokens(entry):
            reqs = BUILD_ENTRY_REQUIREMENTS.get(token)
            if reqs is None:
                continue
            recognized_any = True
            checked += 1

            # A make/ninja step after a satisfied configure precursor consumes
            # generated build files; do not require them to pre-exist.
            if token in GENERATED_BY_CONFIGURE and configure_satisfied:
                continue

            token_ok = True
            for group in reqs:
                if not any((comp_dir / fn).exists() for fn in group):
                    token_ok = False
                    pretty = " / ".join(group)
                    out.append(
                        Finding(
                            "error",
                            cw,
                            f"buildEntry {entry!r} needs one of [{pretty}] at {path!r}, none found",
                        )
                    )
            if token in CONFIGURE_PRECURSORS and token_ok:
                configure_satisfied = True
        if not recognized_any:
            out.append(
                Finding(
                    "warn",
                    cw,
                    f"buildEntry {entry!r} uses no recognized build tool; cannot smoke-check",
                )
            )
    return out


# --------------------------------------------------------------------------- #
def _report(title: str, findings: list[Finding]) -> int:
    errors = [f for f in findings if f.level == "error"]
    warns = [f for f in findings if f.level == "warn"]
    if not errors and not warns:
        print(f"{title}: OK")
    else:
        summary = []
        if errors:
            summary.append(f"{len(errors)} error(s)")
        if warns:
            summary.append(f"{len(warns)} warning(s)")
        print(f"{title}: " + ", ".join(summary))
        for f in findings:
            print(f)
    return len(errors)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Provenance + build-entry checks for shapes.json")
    ap.add_argument("--shapes", default=str(REPO_ROOT / "shapes.json"))
    ap.add_argument("--root", default=str(REPO_ROOT))
    args = ap.parse_args(argv)

    shapes_path = Path(args.shapes)
    repo_root = Path(args.root).resolve()

    if not shapes_path.exists():
        print(f"FAIL: shapes file not found: {shapes_path}", file=sys.stderr)
        return 2
    try:
        data = json.loads(shapes_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"FAIL: {shapes_path} is not valid JSON: {e}", file=sys.stderr)
        return 2

    prov = check_provenance(data, repo_root)
    build = check_build_entries(data, repo_root)

    n_err = 0
    n_err += _report("PROVENANCE/COMPLIANCE", prov)
    n_err += _report("BUILD-ENTRY SMOKE", build)

    n_shapes = len(data.get("shapes") or {})
    if n_err:
        print(f"\nshapes.json content checks FAILED ({n_err} error(s), {n_shapes} shape(s))")
        return 1
    print(f"\nshapes.json content checks PASSED ({n_shapes} shape(s))")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
