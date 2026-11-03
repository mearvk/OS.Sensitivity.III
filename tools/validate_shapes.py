#!/usr/bin/env python3
"""Validate shapes.json for the OS.Sensitivity.III warehouse.

Two layers of checking, both required for the SLeeLa OS Creator
(SLOSSensitivitySource / SLOSSensitivityFetch) to trust the descriptor:

  1. STRUCTURE  -- shapes.json conforms to schemas/shapes-1.0.json.
                   If the `jsonschema` package is installed it is used for a
                   full Draft 2020-12 validation; otherwise a built-in
                   stdlib-only subset validator runs (no third-party deps).
  2. RESOLUTION -- every repo-relative `path`, `sources`, `compliance`,
                   `licenses`, `build`, and shape `root` declared in
                   shapes.json actually exists on disk. This is the property
                   the OSCreator runnable depends on: a shape must resolve
                   deterministically to real source before it is fetched.

Exit status is 0 only when both layers pass. No network access.

Usage:
    python3 tools/validate_shapes.py
    python3 tools/validate_shapes.py --shapes shapes.json --schema schemas/shapes-1.0.json
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Keys inside a shape (and its components) that name a repo-relative path and
# therefore must resolve on disk.
SHAPE_PATH_KEYS = ("root", "compliance", "licenses", "sources", "build", "toolchain", "orchestrator")
COMPONENT_PATH_KEYS = ("path", "sources")


class Problem:
    __slots__ = ("where", "msg")

    def __init__(self, where: str, msg: str) -> None:
        self.where = where
        self.msg = msg

    def __str__(self) -> str:
        return f"  [{self.where}] {self.msg}"


# --------------------------------------------------------------------------- #
# Layer 1: structure                                                          #
# --------------------------------------------------------------------------- #
def validate_structure(data, schema_path: Path) -> list[Problem]:
    """Validate shapes.json against the schema.

    Prefers the `jsonschema` package; falls back to a minimal stdlib check so
    the validator runs with zero third-party dependencies.
    """
    problems: list[Problem] = []
    schema = json.loads(schema_path.read_text(encoding="utf-8"))

    try:
        import jsonschema  # type: ignore

        validator_cls = jsonschema.validators.validator_for(schema)
        validator_cls.check_schema(schema)
        validator = validator_cls(schema)
        for err in sorted(validator.iter_errors(data), key=lambda e: list(e.path)):
            loc = "/".join(str(p) for p in err.path) or "<root>"
            problems.append(Problem(f"schema:{loc}", err.message))
        return problems
    except ImportError:
        pass  # fall through to the built-in subset validator

    # ---- stdlib-only subset validator -----------------------------------
    rel_path_re = re.compile(r"^(?!/)(?!.*(^|/)\.\.(/|$))[^\\]+$")

    def req(obj, key, where, typ=None):
        if not isinstance(obj, dict) or key not in obj:
            problems.append(Problem(where, f"missing required key '{key}'"))
            return False
        if typ is not None and not isinstance(obj[key], typ):
            problems.append(Problem(where, f"key '{key}' must be {typ.__name__}"))
            return False
        return True

    def check_rel(value, where):
        if isinstance(value, str) and not rel_path_re.match(value):
            problems.append(Problem(where, f"not a safe repo-relative path: {value!r}"))

    if not isinstance(data, dict):
        problems.append(Problem("<root>", "top-level value must be an object"))
        return problems

    req(data, "warehouse", "<root>", str)
    req(data, "contractVersion", "<root>", str)
    if isinstance(data.get("contractVersion"), str) and not re.match(
        r"^[0-9]+\.[0-9]+$", data["contractVersion"]
    ):
        problems.append(Problem("<root>", "contractVersion must look like '1.0'"))

    if not req(data, "shapes", "<root>", dict):
        return problems
    if not data["shapes"]:
        problems.append(Problem("shapes", "at least one shape is required"))

    valid_status = {"populated", "partial", "planned", "empty"}
    for name, shape in data["shapes"].items():
        w = f"shapes/{name}"
        if not isinstance(shape, dict):
            problems.append(Problem(w, "shape must be an object"))
            continue
        req(shape, "title", w, str)
        req(shape, "root", w, str)
        req(shape, "status", w, str)
        if shape.get("status") not in valid_status:
            problems.append(
                Problem(w, f"status must be one of {sorted(valid_status)}")
            )
        for k in SHAPE_PATH_KEYS:
            if k in shape:
                check_rel(shape[k], f"{w}/{k}")

        for bag in ("components", "paths"):
            if bag not in shape:
                continue
            if not isinstance(shape[bag], dict):
                problems.append(Problem(f"{w}/{bag}", "must be an object"))
                continue
            for cname, comp in shape[bag].items():
                cw = f"{w}/{bag}/{cname}"
                if not isinstance(comp, dict):
                    problems.append(Problem(cw, "component must be an object"))
                    continue
                req(comp, "path", cw, str)
                for k in COMPONENT_PATH_KEYS:
                    if k in comp:
                        check_rel(comp[k], f"{cw}/{k}")

    return problems


# --------------------------------------------------------------------------- #
# Layer 2: on-disk resolution                                                 #
# --------------------------------------------------------------------------- #
def collect_declared_paths(data) -> list[tuple[str, str]]:
    """Return (json-location, repo-relative-path) for every declared path."""
    out: list[tuple[str, str]] = []
    for name, shape in (data.get("shapes") or {}).items():
        if not isinstance(shape, dict):
            continue
        w = f"shapes/{name}"
        for k in SHAPE_PATH_KEYS:
            v = shape.get(k)
            if isinstance(v, str):
                out.append((f"{w}/{k}", v))
        for bag in ("components", "paths"):
            for cname, comp in (shape.get(bag) or {}).items():
                if not isinstance(comp, dict):
                    continue
                cw = f"{w}/{bag}/{cname}"
                for k in COMPONENT_PATH_KEYS:
                    v = comp.get(k)
                    if isinstance(v, str):
                        out.append((f"{cw}/{k}", v))
    return out


def validate_resolution(data, repo_root: Path) -> list[Problem]:
    problems: list[Problem] = []
    for where, rel in collect_declared_paths(data):
        # Guard against path escape even if the schema layer was skipped.
        target = (repo_root / rel).resolve()
        try:
            target.relative_to(repo_root)
        except ValueError:
            problems.append(Problem(where, f"path escapes the repo root: {rel!r}"))
            continue
        if not os.path.exists(target):
            problems.append(Problem(where, f"path does not exist on disk: {rel!r}"))
    return problems


# --------------------------------------------------------------------------- #
def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Validate shapes.json")
    ap.add_argument("--shapes", default=str(REPO_ROOT / "shapes.json"))
    ap.add_argument("--schema", default=str(REPO_ROOT / "schemas" / "shapes-1.0.json"))
    ap.add_argument(
        "--root",
        default=str(REPO_ROOT),
        help="Repo root used to resolve declared paths (default: repo containing this script).",
    )
    args = ap.parse_args(argv)

    shapes_path = Path(args.shapes)
    schema_path = Path(args.schema)
    repo_root = Path(args.root).resolve()

    if not shapes_path.exists():
        print(f"FAIL: shapes file not found: {shapes_path}", file=sys.stderr)
        return 2
    if not schema_path.exists():
        print(f"FAIL: schema file not found: {schema_path}", file=sys.stderr)
        return 2

    try:
        data = json.loads(shapes_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"FAIL: {shapes_path} is not valid JSON: {e}", file=sys.stderr)
        return 2

    structure = validate_structure(data, schema_path)
    resolution = validate_resolution(data, repo_root)

    n_shapes = len(data.get("shapes") or {})
    n_paths = len(collect_declared_paths(data))

    if structure:
        print(f"STRUCTURE: {len(structure)} problem(s)")
        for p in structure:
            print(p)
    else:
        print("STRUCTURE: OK")

    if resolution:
        print(f"RESOLUTION: {len(resolution)} problem(s)")
        for p in resolution:
            print(p)
    else:
        print(f"RESOLUTION: OK ({n_paths} declared path(s) resolve on disk)")

    if structure or resolution:
        print(f"\nshapes.json INVALID ({n_shapes} shape(s) checked)")
        return 1

    print(f"\nshapes.json VALID ({n_shapes} shape(s), {n_paths} path(s))")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
