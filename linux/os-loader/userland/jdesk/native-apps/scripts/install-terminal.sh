#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# install-terminal.sh — Install JDesk Terminal components
#
# The terminal itself runs GOVERNED through JDesk's memory-guard.
# Even shell sessions have resource ceilings — no single terminal
# process can exhaust system RAM or fork bomb the host.
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

INSTALL_DIR="${1:-/opt/jdesk/apps/terminal}"
MANIFESTS_DIR="/opt/jdesk/manifests"
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${GREEN}[JDesk]${NC} Terminal Components Installer"
echo "  Target: $INSTALL_DIR"
echo "  Size:   ~2 MB (symlinks + tmux)"
echo "  Mode:   GOVERNED (all execution via java -memory-guard)"
echo ""

# Check if already set up
if [ -x "$INSTALL_DIR/jdesk-terminal" ]; then
    echo -e "${YELLOW}[SKIP]${NC} JDesk terminal already configured."
    exit 0
fi

# Create directory
echo -e "${GREEN}[1/5]${NC} Creating terminal directory..."
mkdir -p "$INSTALL_DIR"

# Symlink shells (these are targets for governed execution)
echo -e "${GREEN}[2/5]${NC} Creating governed shell references..."
ln -sf /usr/bin/bash "$INSTALL_DIR/bash"
ln -sf /usr/bin/sh "$INSTALL_DIR/sh"

if command -v zsh &>/dev/null; then
    ln -sf "$(which zsh)" "$INSTALL_DIR/zsh"
    echo "  ✓ zsh"
fi

if command -v fish &>/dev/null; then
    ln -sf "$(which fish)" "$INSTALL_DIR/fish"
    echo "  ✓ fish"
fi

echo "  ✓ bash"
echo "  ✓ sh"

# Install tmux if not present
echo -e "${GREEN}[3/5]${NC} Ensuring tmux is installed..."
if command -v tmux &>/dev/null; then
    echo -e "  ${YELLOW}[ALREADY]${NC} tmux present"
    ln -sf "$(which tmux)" "$INSTALL_DIR/tmux"
else
    apt-get update -qq
    apt-get install -y --no-install-recommends tmux
    ln -sf /usr/bin/tmux "$INSTALL_DIR/tmux"
    echo -e "  ${GREEN}✓${NC} tmux installed"
fi

# Create JDesk terminal wrapper (this is what memory-guard actually launches)
echo -e "${GREEN}[4/5]${NC} Creating JDesk terminal wrapper..."
cat > "$INSTALL_DIR/jdesk-terminal" << 'WRAPPER'
#!/bin/bash
# JDesk Terminal — Governed Shell Session
#
# This script is launched BY java -memory-guard, not directly.
# Resource limits (RAM, CPU, threads, fork count) are enforced
# by the JVM Memory Proxy wrapping this process.
#
# The terminal session runs INSIDE JDesk's security perimeter.

export TERM="${TERM:-xterm-256color}"
export JDESK_TERMINAL=1
export JDESK_GOVERNED=1
export PS1='jdesk:\w$ '

# Suppress nvm warning about conflicting npm_config_prefix (set by IDEs like IntelliJ)
unset npm_config_prefix

# Use user's preferred shell, or bash
SHELL_BIN="${SHELL:-/bin/bash}"

if [ "$1" = "--tmux" ] || [ "$1" = "-t" ]; then
    shift
    exec tmux new-session -s "jdesk-$$" "$SHELL_BIN" "$@"
else
    exec "$SHELL_BIN" "$@"
fi
WRAPPER
chmod +x "$INSTALL_DIR/jdesk-terminal"

# Register with JDesk governance
echo -e "${GREEN}[5/5]${NC} Registering with JDesk governance..."
mkdir -p "$MANIFESTS_DIR"
cat > "$MANIFESTS_DIR/terminal.jdesk-app" << EOF
# JDesk Application Manifest: Terminal
# ALL execution goes through java -memory-guard
# Even shell sessions have resource ceilings.
name=Terminal
binary=$INSTALL_DIR/jdesk-terminal
icon=/opt/jdesk/icons/terminal.svg
profile=terminal
category=system
desktop=true
panel=true
ram-soft=64m
ram-hard=256m
cpu=50
threads=16
disk-write=50m
EOF

# Also link into /opt/jdesk/bin
mkdir -p /opt/jdesk/bin
ln -sf "$INSTALL_DIR/jdesk-terminal" /opt/jdesk/bin/jdesk-terminal

echo ""
echo "═══════════════════════════════════════════════════"
echo "  ✓ JDesk Terminal installed to $INSTALL_DIR"
echo "  Shells: bash, sh$(command -v zsh &>/dev/null && echo ', zsh')$(command -v fish &>/dev/null && echo ', fish')"
echo "  Extras: tmux"
echo "  Manifest: $MANIFESTS_DIR/terminal.jdesk-app"
echo ""
echo "  GOVERNANCE: Terminal runs through:"
echo "    java -memory-guard -Xguard:profile=terminal $INSTALL_DIR/jdesk-terminal"
echo "  RAM capped at 256 MB, CPU at 50%, 16 threads max."
echo "  Fork bombs are blocked by the Memory Proxy."
echo "═══════════════════════════════════════════════════"
