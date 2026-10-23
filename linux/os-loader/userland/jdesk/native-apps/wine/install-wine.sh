#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# install-wine.sh — Install Wine (Windows PE execution layer) for JDesk
#
# Provides the ability to run Windows .dll and .exe files from the
# JDesk desktop via the LibraryLinker co-linking engine.
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

INSTALL_DIR="${1:-/opt/jdesk/apps}"
WINE_PREFIX="/opt/jdesk/wine-prefix"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${GREEN}[JDesk]${NC} Wine Installer (Windows PE Support)"
echo "  Target:      $INSTALL_DIR"
echo "  WINEPREFIX:  $WINE_PREFIX"
echo "  Size:        ~400 MB"
echo ""

# Check if already installed
if command -v wine64 &>/dev/null && [ -d "$WINE_PREFIX" ]; then
    echo -e "${YELLOW}[SKIP]${NC} Wine already installed and configured."
    wine64 --version 2>/dev/null || true
    exit 0
fi

# Step 1: Enable 32-bit architecture (needed for wine32)
echo -e "${GREEN}[1/5]${NC} Enabling i386 architecture..."
if ! dpkg --print-foreign-architectures | grep -q i386; then
    dpkg --add-architecture i386
    apt-get update -qq
    echo "  ✓ i386 architecture enabled"
else
    echo -e "  ${YELLOW}[ALREADY]${NC} i386 already enabled"
fi

# Step 2: Install Wine packages
echo -e "${GREEN}[2/5]${NC} Installing Wine packages..."
apt-get install -y --no-install-recommends \
    wine64 \
    wine32 \
    wine \
    winbind \
    cabextract

# Step 3: Create WINEPREFIX
echo -e "${GREEN}[3/5]${NC} Initializing Wine prefix..."
mkdir -p "$WINE_PREFIX"
export WINEPREFIX="$WINE_PREFIX"
export WINEDEBUG="-all"

# Initialize the prefix (creates the Windows directory structure)
if [ ! -f "$WINE_PREFIX/system.reg" ]; then
    wineboot --init 2>/dev/null || true
    # Wait for wineserver to finish
    wineserver --wait 2>/dev/null || true
    echo "  ✓ Wine prefix initialized"
else
    echo -e "  ${YELLOW}[ALREADY]${NC} Wine prefix exists"
fi

# Step 4: Create JDesk symlinks
echo -e "${GREEN}[4/5]${NC} Creating JDesk symlinks..."
mkdir -p "$INSTALL_DIR/runtimes"
ln -sf "$(which wine64)" "$INSTALL_DIR/runtimes/wine64"
ln -sf "$(which wine)" "$INSTALL_DIR/runtimes/wine"

mkdir -p /opt/jdesk/bin
ln -sf "$(which wine64)" /opt/jdesk/bin/wine
ln -sf "$(which wineboot)" /opt/jdesk/bin/wineboot

# Step 5: Install jdesk-dllhost.exe if available
echo -e "${GREEN}[5/5]${NC} Installing JDesk DLL host..."
DLLHOST_SRC="$SCRIPT_DIR/../launcher/jdesk-dllhost.exe"
DLLHOST_DEST="$WINE_PREFIX/drive_c/jdesk/jdesk-dllhost.exe"

if [ -f "$DLLHOST_SRC" ]; then
    mkdir -p "$WINE_PREFIX/drive_c/jdesk"
    cp -f "$DLLHOST_SRC" "$DLLHOST_DEST"
    # Also put in /opt/jdesk/bin for the LibraryLinker to find
    cp -f "$DLLHOST_SRC" /opt/jdesk/bin/jdesk-dllhost.exe
    echo "  ✓ jdesk-dllhost.exe installed"
else
    echo -e "  ${YELLOW}[SKIP]${NC} jdesk-dllhost.exe not found (build with: make compile-native)"
    echo "          Expected at: $DLLHOST_SRC"
fi

# Create Wine environment config for JDesk
cat > /opt/jdesk/wine.env << EOF
# JDesk Wine Environment Configuration
# Source this before running Wine commands:
#   source /opt/jdesk/wine.env

export WINEPREFIX="$WINE_PREFIX"
export WINEDEBUG="-all"
export WINEARCH="win64"
EOF

# Verify
echo ""
if command -v wine64 &>/dev/null; then
    VERSION=$(wine64 --version 2>/dev/null || echo "unknown")
    echo "═══════════════════════════════════════════════════"
    echo "  ✓ Wine installed: $VERSION"
    echo "  Prefix:   $WINE_PREFIX"
    echo "  Symlinks: $INSTALL_DIR/runtimes/wine64"
    echo "  DLLHost:  ${DLLHOST_DEST:-not installed}"
    echo "  Config:   /opt/jdesk/wine.env"
    echo ""
    echo "  Windows .dll and .exe files can now be launched"
    echo "  from JDesk via the LibraryLinker co-linking engine."
    echo "═══════════════════════════════════════════════════"
else
    echo -e "${RED}[ERROR]${NC} Wine installation failed."
    exit 1
fi
