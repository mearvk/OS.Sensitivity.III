#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# fetch-intellij-source.sh — Fetch IntelliJ IDEA Community Edition source
#
# Clones the IntelliJ Community Edition source from the official JetBrains
# GitHub repository. This provides GPL compliance, offline build capability,
# and serves as the backend intelligence for JDesk IDE.
#
# Source: github.com/JetBrains/intellij-community (Apache-2.0)
# Size: ~2.5 GB (shallow clone)
# Build: Requires JDK 17+, Gradle
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_DIR="${1:-$SCRIPT_DIR/intellij-community-src}"
REPO_URL="https://github.com/JetBrains/intellij-community.git"
CLONE_DEPTH=10
BRANCH="master"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  IntelliJ IDEA Community Edition — Source Fetch${NC}"
echo -e "${GREEN}  Galactic Cherry Marvell Edition 98${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo "  Repository: $REPO_URL"
echo "  Branch:     $BRANCH"
echo "  Depth:      $CLONE_DEPTH commits (shallow)"
echo "  Target:     $SOURCE_DIR"
echo ""

# ============================================================================
#  Prerequisites
# ============================================================================

echo -e "${GREEN}[1/5]${NC} Checking prerequisites..."

if ! command -v git &>/dev/null; then
    echo -e "${RED}[ERROR]${NC} git not found. Install with: apt install git"
    exit 1
fi

# Check disk space (need ~3 GB for source)
PARENT_DIR="$(dirname "$SOURCE_DIR")"
mkdir -p "$PARENT_DIR"
AVAIL_MB=$(df -BM "$PARENT_DIR" 2>/dev/null | tail -1 | awk '{print $4}' | tr -d 'M')
if [ -n "$AVAIL_MB" ] && [ "$AVAIL_MB" -lt 3072 ]; then
    echo -e "${RED}[ERROR]${NC} Insufficient disk space: ${AVAIL_MB} MB available, need ~3 GB"
    exit 1
fi
echo "  ✓ git available"
echo "  ✓ Disk space: ${AVAIL_MB:-unknown} MB available"
echo ""

# ============================================================================
#  Check existing source
# ============================================================================

echo -e "${GREEN}[2/5]${NC} Checking for existing source..."

if [ -d "$SOURCE_DIR/.git" ]; then
    echo -e "${YELLOW}[EXISTS]${NC} Source already present at $SOURCE_DIR"
    echo "  Updating with git pull..."
    cd "$SOURCE_DIR"
    git pull --depth=$CLONE_DEPTH origin $BRANCH 2>/dev/null || \
        echo -e "${YELLOW}  (pull failed — using existing source)${NC}"
    echo ""
    echo -e "${GREEN}[DONE]${NC} IntelliJ source up to date."
    exit 0
fi

if [ -d "$SOURCE_DIR" ] && [ -f "$SOURCE_DIR/GALACTIC_CHERRY_SOURCE_INFO" ]; then
    echo -e "${YELLOW}[EXISTS]${NC} Source present (git history stripped for distribution)."
    exit 0
fi

# ============================================================================
#  Clone
# ============================================================================

echo -e "${GREEN}[3/5]${NC} Cloning IntelliJ Community Edition (shallow, depth=$CLONE_DEPTH)..."
echo "  This will download approximately 2.5 GB..."
echo ""

git clone --depth=$CLONE_DEPTH --branch "$BRANCH" --single-branch \
    "$REPO_URL" "$SOURCE_DIR"

echo ""
echo -e "${GREEN}[4/5]${NC} Recording provenance..."

# Record source info
COMMIT=$(cd "$SOURCE_DIR" && git rev-parse HEAD 2>/dev/null || echo "unknown")
cat > "$SOURCE_DIR/GALACTIC_CHERRY_SOURCE_INFO" << EOF
Source: IntelliJ IDEA Community Edition
Repository: $REPO_URL
Branch: $BRANCH
Commit: $COMMIT
Fetched: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
Depth: $CLONE_DEPTH
Distribution: Galactic Cherry Marvell Edition 98
License: Apache-2.0
Purpose: IDE backend intelligence for JDesk IDE (full IntelliJ feature parity)
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
echo "  ✓ IntelliJ Community Edition source installed"
echo ""
echo "  Location:   $SOURCE_DIR"
echo "  Size:       $SIZE"
echo "  Files:      $FILE_COUNT"
echo "  Commit:     ${COMMIT:0:10}"
echo "  License:    Apache-2.0"
echo ""
echo "  Build instructions:"
echo "    cd $SOURCE_DIR"
echo "    ./gradlew :community-main:buildSearchableOptions"
echo "    ./gradlew :community-main:assemble"
echo ""
echo "  Or install pre-built:"
echo "    apt install intellij-idea-community"
echo "    snap install intellij-idea-community --classic"
echo ""
echo "  JDesk IDE (us.mearvk.jdesk.apps.JDeskIDE) provides the"
echo "  full IntelliJ GUI (13 menus, 130+ actions) in JavaFX."
echo "  IntelliJ backend connects for code intelligence when present."
echo "═══════════════════════════════════════════════════════════"
