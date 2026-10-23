#!/bin/bash
# Build the SecureJDK 28 Transition Supervisor + MySQL Admin (pure JDK, no deps).
#   ./build.sh          compile to out/
#   ./build.sh run      compile + run the supervisor on the default local pipe
#   ./build.sh admin .. compile + run the admin CLI with the given args
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/out"
SRC="$HERE/src"

mkdir -p "$OUT"
echo "=== compiling transition-supervisor (JDK $(javac -version 2>&1 | cut -d' ' -f2)) ==="
find "$SRC" -name '*.java' > "$OUT/sources.txt"
javac -d "$OUT" @"$OUT/sources.txt"
echo "OK -> $OUT"

case "$1" in
  run)   shift; exec java -cp "$OUT" com.mearvk.securejdk.transition.Supervisor "$@" ;;
  admin) shift; exec java -cp "$OUT" com.mearvk.securejdk.transition.Admin "$@" ;;
  demo)  shift; exec java -cp "$OUT" com.mearvk.securejdk.transition.LoopbackDemo "$@" ;;
esac
