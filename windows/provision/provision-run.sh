#!/usr/bin/env bash
# First-boot provisioning runner for an apt-provisioned shape.
#
# Reads a provision.json (schemas/provision-1.0.json), points apt at the pinned
# snapshot mirror for a deterministic first boot, installs the declared
# packages (honoring exact version pins when reproducibility.pinVersions is
# true), and runs the firstBoot commands. Invoked by sleela-provision.service
# or directly by the OS Creator.
#
# Only handles provisioner.type == "apt". For non-apt shapes (rapps/pkg/none)
# it exits 0 with a note -- those shapes are baked or provisioned natively.
#
# No Microsoft code, SDK, binaries, or license is used.
#
# Usage: provision-run.sh /path/to/provision.json
set -euo pipefail

CONF="${1:?usage: provision-run.sh <provision.json>}"
[ -r "$CONF" ] || { echo "provision-run: cannot read $CONF" >&2; exit 1; }

# Minimal JSON reader via python3 (present on an Ubuntu base).
jq_py() { python3 - "$CONF" "$1" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
expr = sys.argv[2]
cur = d
for part in expr.split('.'):
    if part == '':
        continue
    cur = cur.get(part) if isinstance(cur, dict) else None
    if cur is None:
        break
if isinstance(cur, (list, dict)):
    print(json.dumps(cur))
elif cur is None:
    print('')
else:
    print(cur)
PY
}

PTYPE="$(jq_py provisioner.type)"
if [ "$PTYPE" != "apt" ]; then
  echo "provision-run: provisioner type '$PTYPE' is not apt; nothing to do (baked/native shape)."
  exit 0
fi

PIN="$(jq_py provisioner.reproducibility.pinVersions)"
SNAP="$(jq_py provisioner.reproducibility.snapshotMirror)"
UPDATE_FIRST="$(jq_py provisioner.updateFirst)"

# Build the package list, honoring version pins.
mapfile -t PKGS < <(python3 - "$CONF" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
pr = d.get("provisioner", {})
pin = bool(pr.get("reproducibility", {}).get("pinVersions"))
for p in pr.get("packages", []):
    name = p.get("name")
    ver = p.get("version")
    if pin and ver:
        print(f"{name}={ver}")
    else:
        print(name)
PY
)

if [ "${SNAP:-}" != "" ]; then
  echo "provision-run: pinning apt to snapshot mirror: $SNAP"
  echo "deb $SNAP $(. /etc/os-release 2>/dev/null; echo "${VERSION_CODENAME:-noble}") main universe" \
    > /etc/apt/sources.list.d/sleela-snapshot.list || true
fi

export DEBIAN_FRONTEND=noninteractive
if [ "$UPDATE_FIRST" = "True" ] || [ "$UPDATE_FIRST" = "true" ]; then
  apt-get update
fi

if [ "${#PKGS[@]}" -gt 0 ]; then
  echo "provision-run: installing: ${PKGS[*]}"
  apt-get install -y --no-install-recommends "${PKGS[@]}"
fi

# Run firstBoot commands in order.
python3 - "$CONF" <<'PY' | while IFS= read -r cmd; do
import json, sys
d = json.load(open(sys.argv[1]))
for c in d.get("provisioner", {}).get("firstBoot", []):
    print(c)
PY
  [ -n "$cmd" ] && sh -c "$cmd" || true
done

echo "provision-run: done (pinVersions=$PIN)."
