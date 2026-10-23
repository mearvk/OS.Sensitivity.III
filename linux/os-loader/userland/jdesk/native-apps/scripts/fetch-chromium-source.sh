#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# fetch-chromium-source.sh — Fetch Chromium browser source
#
# Clones the Chromium browser source from the official GitHub mirror.
# This provides GPL compliance, offline build capability, and serves
# as the rendering engine for both JDesk Browser and Dave's web interface.
#
# Source: github.com/chromium/chromium (BSD-3-Clause)
# Size: ~5.5 GB (shallow clone)
# Build: Requires depot_tools (gn + autoninja), 16 GB RAM, 100+ GB disk
#
# Note: The OS-level Chromium source already exists at:
#   userland/chromium/chromium-src/
# This script fetches specifically for the JDesk native-apps context,
# or symlinks to the existing source if present.
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_DIR="${1:-$SCRIPT_DIR/chromium-src}"
REPO_URL="https://github.com/chromium/chromium.git"
CLONE_DEPTH=10
BRANCH="main"

# Check if OS-level chromium source already exists
# From native-apps/scripts/ → ../../.. gets to userland/, then chromium/chromium-src
OS_CHROMIUM_SRC="$(cd "$SCRIPT_DIR/../../.." 2>/dev/null && pwd)/chromium/chromium-src"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  Chromium Browser — Source Fetch${NC}"
echo -e "${GREEN}  Galactic Cherry Marvell Edition 98${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo "  Repository: $REPO_URL"
echo "  Branch:     $BRANCH"
echo "  Depth:      $CLONE_DEPTH commits (shallow)"
echo "  Target:     $SOURCE_DIR"
echo ""

# ============================================================================
#  Check for existing OS-level Chromium source
# ============================================================================

echo -e "${GREEN}[1/5]${NC} Checking for existing Chromium source..."

if [ -d "$OS_CHROMIUM_SRC" ] && [ -f "$OS_CHROMIUM_SRC/BUILD.gn" ]; then
    echo -e "${YELLOW}[EXISTS]${NC} OS-level Chromium source found at:"
    echo "  $OS_CHROMIUM_SRC"
    echo ""
    echo "  Creating symlink instead of duplicate clone..."
    ln -sfn "$OS_CHROMIUM_SRC" "$SOURCE_DIR"
    echo "  $SOURCE_DIR → $OS_CHROMIUM_SRC"
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  ✓ Chromium source linked (no duplicate download needed)"
    echo "  ✓ Build with: cd $SOURCE_DIR && gn gen out/Release && autoninja -C out/Release chrome"
    echo "═══════════════════════════════════════════════════════════"
    exit 0
fi

if [ -d "$SOURCE_DIR/.git" ]; then
    echo -e "${YELLOW}[EXISTS]${NC} Source already present at $SOURCE_DIR"
    echo "  Updating with git pull..."
    cd "$SOURCE_DIR"
    git pull --depth=$CLONE_DEPTH origin $BRANCH 2>/dev/null || \
        echo -e "${YELLOW}  (pull failed — using existing source)${NC}"
    echo ""
    echo -e "${GREEN}[DONE]${NC} Chromium source up to date."
    exit 0
fi

if [ -d "$SOURCE_DIR" ] && [ -f "$SOURCE_DIR/GALACTIC_CHERRY_SOURCE_INFO" ]; then
    echo -e "${YELLOW}[EXISTS]${NC} Source present (git history stripped for distribution)."
    exit 0
fi

# ============================================================================
#  Prerequisites
# ============================================================================

echo -e "${GREEN}[2/5]${NC} Checking prerequisites..."

if ! command -v git &>/dev/null; then
    echo -e "${RED}[ERROR]${NC} git not found. Install with: apt install git"
    exit 1
fi

# Check disk space (need ~6 GB for source)
PARENT_DIR="$(dirname "$SOURCE_DIR")"
mkdir -p "$PARENT_DIR"
AVAIL_MB=$(df -BM "$PARENT_DIR" 2>/dev/null | tail -1 | awk '{print $4}' | tr -d 'M')
if [ -n "$AVAIL_MB" ] && [ "$AVAIL_MB" -lt 6144 ]; then
    echo -e "${RED}[ERROR]${NC} Insufficient disk space: ${AVAIL_MB} MB available, need ~6 GB"
    echo "  Chromium source is approximately 5.5 GB."
    echo "  Consider using the pre-installed OS-level source at:"
    echo "    userland/chromium/chromium-src/"
    exit 1
fi
echo "  ✓ git available"
echo "  ✓ Disk space: ${AVAIL_MB:-unknown} MB available"
echo ""

# ============================================================================
#  Clone
# ============================================================================

echo -e "${GREEN}[3/5]${NC} Cloning Chromium (shallow, depth=$CLONE_DEPTH)..."
echo "  This will download approximately 5.5 GB. Be patient..."
echo ""

git clone --depth=$CLONE_DEPTH --branch "$BRANCH" --single-branch \
    --filter=blob:limit=10M \
    "$REPO_URL" "$SOURCE_DIR"

echo ""
echo -e "${GREEN}[4/5]${NC} Recording provenance..."

COMMIT=$(cd "$SOURCE_DIR" && git rev-parse HEAD 2>/dev/null || echo "unknown")
cat > "$SOURCE_DIR/GALACTIC_CHERRY_SOURCE_INFO" << EOF
Source: Chromium Browser
Repository: $REPO_URL
Branch: $BRANCH
Commit: $COMMIT
Fetched: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
Depth: $CLONE_DEPTH
Distribution: Galactic Cherry Marvell Edition 98
License: BSD-3-Clause
Purpose: Web browser for JDesk + headless rendering engine for Dave AI
EOF

echo "  Commit: $COMMIT"
echo "  Provenance marker written."
echo ""

# ============================================================================
#  Summary
# ============================================================================

echo -e "${GREEN}[5/5]${NC} Source fetch complete."
echo ""

FILE_COUNT=$(find "$SOURCE_DIR" -type f 2>/dev/null | wc -l)
SIZE=$(du -sh "$SOURCE_DIR" 2>/dev/null | cut -f1)

echo "═══════════════════════════════════════════════════════════"
echo "  ✓ Chromium browser source installed"
echo ""
echo "  Location:   $SOURCE_DIR"
echo "  Size:       $SIZE"
echo "  Files:      $FILE_COUNT"
echo "  Commit:     ${COMMIT:0:10}"
echo "  License:    BSD-3-Clause"
echo ""
echo "  Build instructions:"
echo "    # Install depot_tools first:"
echo "    git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git"
echo "    export PATH=\$PATH:\$(pwd)/depot_tools"
echo ""
echo "    cd $SOURCE_DIR"
echo "    gn gen out/Release --args='"
echo "        is_debug=false"
echo "        is_component_build=false"
echo "        symbol_level=0"
echo "        enable_nacl=false"
echo "        use_cups=true"
echo "        use_dbus=true'"
echo "    autoninja -C out/Release chrome"
echo ""
echo "  Or install pre-built:"
echo "    apt install chromium-browser"
echo ""
echo "  Build requirements: 16 GB RAM, 100+ GB disk, 2-4 hours"
echo ""
echo "  Integration:"
echo "    • JDesk Browser (us.mearvk.jdesk.apps.JDeskBrowser)"
echo "    • Dave AI web interface (headless mode via --headless=new)"
echo "    • Dave screenshots (--screenshot at 1920x1080)"
echo "    • Dave DOM extraction (--dump-dom)"
echo "═══════════════════════════════════════════════════════════"
