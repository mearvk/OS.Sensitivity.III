#!/usr/bin/env bash
set -euo pipefail
# Orca is primarily Python; this wrapper verifies its Python/package targets
# before delegating to the local module builder.
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; SRC="$ROOT/orca/source"
[ -d "$SRC" ] || { echo "ERROR: missing Orca source/" >&2; exit 1; }
[ -e "$SRC/meson.build" ] || [ -e "$SRC/pyproject.toml" ] || [ -e "$SRC/setup.py" ] || { echo "ERROR: unusual Orca source: no Meson/Python build target" >&2; exit 1; }
if [ -e "$SRC/meson.build" ]; then exec "$ROOT/build-module.sh" orca "$@"; fi
python3 -m build --wheel --outdir "$ROOT/orca/build-local" "$SRC" "$@"
