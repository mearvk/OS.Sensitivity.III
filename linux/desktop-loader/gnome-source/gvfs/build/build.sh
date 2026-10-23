#!/usr/bin/env bash
set -euo pipefail
# Local GVfs build entry point; validates source/build targets first.
exec "$(cd "$(dirname "$0")/../.." && pwd)/build-module.sh" gvfs "$@"
