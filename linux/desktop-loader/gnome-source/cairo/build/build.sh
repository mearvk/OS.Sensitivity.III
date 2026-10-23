#!/usr/bin/env bash
set -euo pipefail
# Local Cairo build entry point. Common builder validates unusual/multiple targets.
exec "$(cd "$(dirname "$0")/../.." && pwd)/build-module.sh" cairo "$@"
