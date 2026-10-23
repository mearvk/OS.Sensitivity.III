#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# install-pcmanfm.sh — Install PCManFM-Qt File Manager for JDesk
#
# All execution is GOVERNED by JDesk's JVM Memory Proxy.
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

INSTALL_DIR="${1:-/opt/jdesk/apps/pcmanfm}"
MANIFESTS_DIR="/opt/jdesk/manifests"
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${GREEN}[JDesk]${NC} PCManFM-Qt File Manager Installer"
echo "  Target: $INSTALL_DIR"
echo "  Size:   ~45 MB"
echo "  Mode:   GOVERNED (all execution via java -memory-guard)"
echo ""

# Check if already installed
if [ -L "$INSTALL_DIR/files" ] && (command -v pcmanfm-qt &>/dev/null || command -v nautilus &>/dev/null); then
    echo -e "${YELLOW}[SKIP]${NC} File manager already installed."
    exit 0
fi

# Determine which variant to install
FM_PKG=""
FM_BIN=""

if apt-cache show pcmanfm-qt &>/dev/null 2>&1; then
    FM_PKG="pcmanfm-qt"
    FM_BIN="/usr/bin/pcmanfm-qt"
elif apt-cache show pcmanfm &>/dev/null 2>&1; then
    FM_PKG="pcmanfm"
    FM_BIN="/usr/bin/pcmanfm"
elif apt-cache show nautilus &>/dev/null 2>&1; then
    FM_PKG="nautilus"
    FM_BIN="/usr/bin/nautilus"
else
    echo -e "${RED}[ERROR]${NC} No file manager available in repos."
    exit 1
fi

echo -e "${GREEN}[1/3]${NC} Installing $FM_PKG..."
apt-get update -qq
apt-get install -y --no-install-recommends "$FM_PKG"

# Create governed binary reference
echo -e "${GREEN}[2/3]${NC} Creating governed binary reference..."
mkdir -p "$INSTALL_DIR"
ln -sf "$FM_BIN" "$INSTALL_DIR/$(basename "$FM_BIN")"
ln -sf "$FM_BIN" "$INSTALL_DIR/files"

# Register with JDesk governance
echo -e "${GREEN}[3/3]${NC} Registering with JDesk governance..."
mkdir -p "$MANIFESTS_DIR"
cat > "$MANIFESTS_DIR/files.jdesk-app" << EOF
# JDesk Application Manifest: File Manager
# ALL execution goes through java -memory-guard
name=Files
binary=$INSTALL_DIR/files
icon=/opt/jdesk/icons/files.svg
profile=files
category=system
desktop=true
panel=true
ram-soft=128m
ram-hard=512m
cpu=40
threads=16
disk-write=100m
EOF

echo ""
echo "═══════════════════════════════════════════════════"
echo "  ✓ File manager installed to $INSTALL_DIR"
echo "  Manifest: $MANIFESTS_DIR/files.jdesk-app"
echo ""
echo "  GOVERNANCE: File manager runs through:"
echo "    java -memory-guard -Xguard:profile=files $INSTALL_DIR/files"
echo "═══════════════════════════════════════════════════"
