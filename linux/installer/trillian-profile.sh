#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Installer Profile: Trillian/Dino is an optional local source-development
# component. It is not pulled or installed implicitly.
TRILLIAN="$ROOT/trillian"

if [[ ! -d "$TRILLIAN/dino" ]]; then
  echo "Trillian profile: source not present; run trillian/pull-dino.sh when desired."
  exit 0
fi

"$TRILLIAN/build-linux.sh"
echo "Trillian profile build completed. No system installation was performed."
