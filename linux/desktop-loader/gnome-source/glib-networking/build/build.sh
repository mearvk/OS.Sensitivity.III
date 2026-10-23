#!/usr/bin/env bash
set -euo pipefail
# Local GLib networking build entry point; validates source/build targets first.
exec "$(cd "$(dirname "$0")/../.." && pwd)/build-module.sh" glib-networking "$@"
