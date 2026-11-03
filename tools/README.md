# tools/

Maintenance tooling for the **OS.Sensitivity.III** warehouse.

## `validate_shapes.py`

Validates the root [`shapes.json`](../shapes.json) descriptor that the SLeeLa
OS Creator (`SLOSSensitivitySource` / `SLOSSensitivityFetch`) resolves to fetch
OS-grade C/C++ source for a chosen shape.

```sh
python3 tools/validate_shapes.py
```

It runs two checks, both required for the descriptor to be trusted:

1. **Structure** — `shapes.json` conforms to the JSON Schema
   [`schemas/shapes-1.0.json`](../schemas/shapes-1.0.json). If the optional
   `jsonschema` package is installed it is used for a full Draft 2020-12
   validation; otherwise a built-in standard-library subset validator runs, so
   the tool works with **no third-party dependencies**.
2. **Resolution** — every repo-relative path declared in `shapes.json`
   (shape `root`, each component `path`, and every `sources` / `compliance` /
   `licenses` / `build` reference) **exists on disk**. This is the property the
   OSCreator runnable depends on: a chosen shape must resolve deterministically
   to real source before it is fetched.

Exit status is `0` only when both checks pass; any problem prints its exact
JSON location and exits non-zero.

### Options

| Flag | Default | Meaning |
|---|---|---|
| `--shapes` | `shapes.json` | descriptor to validate |
| `--schema` | `schemas/shapes-1.0.json` | schema to validate against |
| `--root` | repo root | base directory for resolving declared paths |

### CI

The validator runs automatically on every push and pull request via
[`.github/workflows/validate-shapes.yml`](../.github/workflows/validate-shapes.yml),
so a descriptor that points at a missing or malformed path cannot land on
`main`.
