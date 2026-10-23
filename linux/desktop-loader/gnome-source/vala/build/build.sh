#!/usr/bin/env bash
set -euo pipefail
# Local Vala build entry point; validates source/build targets first.
exec "$(cd "$(dirname "$0")/../.." && pwd)/build-module.sh" vala "$@"
