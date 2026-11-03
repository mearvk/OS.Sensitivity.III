#!/usr/bin/env python3
"""Validate a per-shape toolchain.json for OS.Sensitivity.III.

Option 2 of the build model: the SLeeLa OS Creator provisions a pinned
toolchain and then compiles an artifact. For that to be deterministic, the
toolchain manifest must (a) conform to its schema and (b) point only at files
that exist: the Dockerfile, the bare-metal setup script, the orchestrator, and
every component source directory it will build.

Checks:
  STRUCTURE   -- toolchain.json conforms to schemas/toolchain-1.0.json
                 (jsonschema if installed, else a stdlib subset check).
  RESOLUTION  -- hostBase.dockerfile / hostBase.bareMetalSetup,
                 orchestrator.script, and each paths[*].component resolve on
                 disk, and the orchestrator + setup scripts are executable.

Standard-library only. No network. Exit 0 only when all checks pass.

Usage:
    python3 tools/validate_toolchain.py windows/toolchain/toolchain.json
    python3 tools/validate_toolchain.py            # discovers all toolchain.json
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SCHEMA = REPO_ROOT / "schemas" / "toolchain-1.0.json"


class Problem:
    __slots__ = ("where", "msg")

    def __init__(self, where, msg):
        self.where, self.msg = where, msg

    def __str__(self):
        return f"  [{self.where}] {self.msg}"


def validate_structure(data, schema_path: Path) -> list[Problem]:
    problems: list[Problem] = []
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    try:
        import jsonschema  # type: ignore

        v = jsonschema.validators.validator_for(schema)(schema)
        for err in sorted(v.iter_errors(data), key=lambda e: list(e.path)):
            loc = "/".join(str(p) for p in err.path) or "<root>"
            problems.append(Problem(f"schema:{loc}", err.message))
        return problems
    except ImportError:
        pass

    def req(obj, key, where, typ=None):
        if not isinstance(obj, dict) or key not in obj:
            problems.append(Problem(where, f"missing required key '{key}'"))
            return False
        if typ is not None and not isinstance(obj[key], typ):
            problems.append(Problem(where, f"key '{key}' must be {typ.__name__}"))
            return False
        return True

    if not isinstance(data, dict):
        problems.append(Problem("<root>", "top-level value must be an object"))
        return problems
    req(data, "shape", "<root>", str)
    req(data, "toolchainVersion", "<root>", str)
    if isinstance(data.get("toolchainVersion"), str) and not re.match(
        r"^[0-9]+\.[0-9]+$", data["toolchainVersion"]
    ):
        problems.append(Problem("<root>", "toolchainVersion must look like '1.0'"))
    if req(data, "hostBase", "<root>", dict):
        req(data["hostBase"], "image", "hostBase", str)
    if req(data, "orchestrator", "<root>", dict):
        req(data["orchestrator"], "script", "orchestrator", str)
    if req(data, "paths", "<root>", dict):
        if not data["paths"]:
            problems.append(Problem("paths", "at least one path required"))
        for name, p in data["paths"].items():
            w = f"paths/{name}"
            req(p, "component", w, str)
            req(p, "buildEntry", w, str)
            req(p, "requires", w, dict)
    return problems


def _rel_targets(data):
    """Yield (where, repo-relative-path, must_be_executable)."""
    hb = data.get("hostBase") or {}
    for k in ("dockerfile", "bareMetalSetup"):
        v = hb.get(k)
        if isinstance(v, str):
            yield (f"hostBase/{k}", v, k == "bareMetalSetup")
    orch = (data.get("orchestrator") or {}).get("script")
    if isinstance(orch, str):
        yield ("orchestrator/script", orch, True)
    for name, p in (data.get("paths") or {}).items():
        comp = p.get("component") if isinstance(p, dict) else None
        if isinstance(comp, str):
            yield (f"paths/{name}/component", comp, False)


def validate_resolution(data, repo_root: Path) -> list[Problem]:
    problems: list[Problem] = []
    for where, rel, must_exec in _rel_targets(data):
        target = (repo_root / rel).resolve()
        try:
            target.relative_to(repo_root)
        except ValueError:
            problems.append(Problem(where, f"path escapes repo root: {rel!r}"))
            continue
        if not target.exists():
            problems.append(Problem(where, f"does not exist on disk: {rel!r}"))
            continue
        if must_exec and target.is_file() and not os.access(target, os.X_OK):
            problems.append(Problem(where, f"script is not executable (chmod +x): {rel!r}"))
    return problems


def discover_manifests(repo_root: Path) -> list[Path]:
    return sorted(repo_root.glob("*/toolchain/toolchain.json")) + sorted(
        repo_root.glob("*/toolchain.json")
    )


def validate_one(path: Path, repo_root: Path) -> int:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"FAIL: {path} is not valid JSON: {e}", file=sys.stderr)
        return 1
    try:
        rel = path.resolve().relative_to(repo_root)
    except ValueError:
        rel = path
    print(f"=== {rel} ===")
    structure = validate_structure(data, SCHEMA)
    resolution = validate_resolution(data, repo_root)
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
        n = sum(1 for _ in _rel_targets(data))
        print(f"RESOLUTION: OK ({n} referenced file(s) resolve)")
    ok = not (structure or resolution)
    print("VALID\n" if ok else "INVALID\n")
    return 0 if ok else 1


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Validate toolchain.json manifests")
    ap.add_argument("manifest", nargs="?", help="path to a toolchain.json (default: discover all)")
    ap.add_argument("--root", default=str(REPO_ROOT))
    args = ap.parse_args(argv)
    repo_root = Path(args.root).resolve()

    if not SCHEMA.exists():
        print(f"FAIL: schema not found: {SCHEMA}", file=sys.stderr)
        return 2

    if args.manifest:
        manifests = [Path(args.manifest)]
    else:
        manifests = discover_manifests(repo_root)
        if not manifests:
            print("No toolchain.json manifests found.")
            return 0

    rc = 0
    for m in manifests:
        rc |= validate_one(m, repo_root)
    print("ALL TOOLCHAIN MANIFESTS VALID" if rc == 0 else "TOOLCHAIN VALIDATION FAILED")
    return rc


if __name__ == "__main__":
    raise SystemExit(main())
