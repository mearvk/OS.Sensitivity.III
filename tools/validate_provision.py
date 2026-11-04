#!/usr/bin/env python3
"""Validate first-boot provisioning descriptors (provision.json) for OS.Sensitivity.III.

The "minimal base ISO + first-boot provisioning from an OS-Creator config"
model: each apt-provisionable shape ships a provision.json that the Creator
injects into a minimal base so packages install on first boot. For that to be
deterministic and honest, the descriptor must conform to its schema and point
only at files that exist.

Checks:
  STRUCTURE   -- provision.json conforms to schemas/provision-1.0.json
                 (jsonschema if installed, else stdlib subset check).
  RESOLUTION  -- firstBootHook.unit / firstBootHook.cloudInit (when declared)
                 resolve on disk.
  COHERENCE   -- apt provisioners with pinVersions=true should either pin a
                 snapshotMirror or per-package versions (reproducibility);
                 reported as a warning. A 'none'/'rapps'/'pkg' provisioner must
                 NOT declare apt packages (error).

Standard-library only. No network. Exit 0 only when no errors (warnings allowed).

Usage:
    python3 tools/validate_provision.py                      # discover all
    python3 tools/validate_provision.py windows/provision/provision.json
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SCHEMA = REPO_ROOT / "schemas" / "provision-1.0.json"


class Finding:
    __slots__ = ("level", "where", "msg")

    def __init__(self, level, where, msg):
        self.level, self.where, self.msg = level, where, msg

    def __str__(self):
        return f"  {'WARN ' if self.level == 'warn' else 'ERROR'} [{self.where}] {self.msg}"


def validate_structure(data, schema_path: Path) -> list[Finding]:
    out: list[Finding] = []
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    try:
        import jsonschema  # type: ignore

        v = jsonschema.validators.validator_for(schema)(schema)
        for err in sorted(v.iter_errors(data), key=lambda e: list(e.path)):
            loc = "/".join(str(p) for p in err.path) or "<root>"
            out.append(Finding("error", f"schema:{loc}", err.message))
        return out
    except ImportError:
        pass

    def req(obj, key, where, typ=None):
        if not isinstance(obj, dict) or key not in obj:
            out.append(Finding("error", where, f"missing required key '{key}'"))
            return False
        if typ is not None and not isinstance(obj[key], typ):
            out.append(Finding("error", where, f"key '{key}' must be {typ.__name__}"))
            return False
        return True

    if not isinstance(data, dict):
        out.append(Finding("error", "<root>", "top-level value must be an object"))
        return out
    req(data, "shape", "<root>", str)
    req(data, "provisionVersion", "<root>", str)
    if isinstance(data.get("provisionVersion"), str) and not re.match(
        r"^[0-9]+\.[0-9]+$", data["provisionVersion"]
    ):
        out.append(Finding("error", "<root>", "provisionVersion must look like '1.0'"))
    if req(data, "base", "<root>", dict):
        req(data["base"], "kind", "base", str)
        req(data["base"], "ref", "base", str)
    if req(data, "provisioner", "<root>", dict):
        req(data["provisioner"], "type", "provisioner", str)
        if data["provisioner"].get("type") not in {"apt", "rapps", "pkg", "none"}:
            out.append(Finding("error", "provisioner/type", "must be apt|rapps|pkg|none"))
    return out


def validate_resolution(data, repo_root: Path) -> list[Finding]:
    out: list[Finding] = []
    hook = data.get("firstBootHook") or {}
    for key in ("unit", "cloudInit"):
        rel = hook.get(key)
        if not isinstance(rel, str):
            continue
        target = (repo_root / rel).resolve()
        try:
            target.relative_to(repo_root)
        except ValueError:
            out.append(Finding("error", f"firstBootHook/{key}", f"escapes repo root: {rel!r}"))
            continue
        if not target.exists():
            out.append(Finding("error", f"firstBootHook/{key}", f"does not exist: {rel!r}"))
    return out


def validate_coherence(data) -> list[Finding]:
    out: list[Finding] = []
    pr = data.get("provisioner") or {}
    ptype = pr.get("type")
    pkgs = pr.get("packages") or []
    if ptype != "apt" and pkgs:
        out.append(
            Finding("error", "provisioner", f"type '{ptype}' must not declare apt packages ({len(pkgs)} found)")
        )
    if ptype == "apt":
        repro = pr.get("reproducibility") or {}
        if repro.get("pinVersions"):
            has_snapshot = bool(repro.get("snapshotMirror"))
            has_versions = any(isinstance(p, dict) and p.get("version") for p in pkgs)
            if not (has_snapshot or has_versions):
                out.append(
                    Finding(
                        "warn",
                        "provisioner/reproducibility",
                        "pinVersions=true but neither snapshotMirror nor any per-package version is set; first boot will not be reproducible",
                    )
                )
    return out


def discover(repo_root: Path) -> list[Path]:
    found: list[Path] = []
    for pat in ("*/provision/*.json", "*/provision.json"):
        found += repo_root.glob(pat)
    return sorted(set(found))


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
    findings = validate_structure(data, SCHEMA) + validate_resolution(data, repo_root) + validate_coherence(data)
    errors = [f for f in findings if f.level == "error"]
    if findings:
        for f in findings:
            print(f)
    status = data.get("status", "active")
    if errors:
        print("INVALID\n")
        return 1
    print(f"VALID ({status})\n")
    return 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Validate provision.json descriptors")
    ap.add_argument("manifest", nargs="?")
    ap.add_argument("--root", default=str(REPO_ROOT))
    args = ap.parse_args(argv)
    repo_root = Path(args.root).resolve()
    if not SCHEMA.exists():
        print(f"FAIL: schema not found: {SCHEMA}", file=sys.stderr)
        return 2
    manifests = [Path(args.manifest)] if args.manifest else discover(repo_root)
    if not manifests:
        print("No provision.json descriptors found.")
        return 0
    rc = 0
    for m in manifests:
        rc |= validate_one(m, repo_root)
    print("ALL PROVISION DESCRIPTORS VALID" if rc == 0 else "PROVISION VALIDATION FAILED")
    return rc


if __name__ == "__main__":
    raise SystemExit(main())
