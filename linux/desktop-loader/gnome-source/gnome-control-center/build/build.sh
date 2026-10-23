#!/usr/bin/env bash
set -euo pipefail
# Local GNOME Control Center build entry point; validates source/build targets first.
exec "$(cd "$(dirname "$0")/../.." && pwd)/build-module.sh" gnome-control-center "$@"
