#!/bin/sh
set -eu

command -v cc >/dev/null 2>&1 || { echo "error: C compiler (cc) not found" >&2; exit 1; }
command -v c++ >/dev/null 2>&1 || { echo "error: C++ compiler (c++) not found" >&2; exit 1; }

make all
printf '%s\n' "Korea evaluation build completed."
