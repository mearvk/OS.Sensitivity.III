#!/usr/bin/env bash
set -euo pipefail
# Local GDK-Pixbuf build entry point; validates source/build targets first.
exec "$(cd "$(dirname "$0")/../.." && pwd)/build-module.sh" gdk-pixbuf "$@"
