#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# install-libreoffice.sh — Install LibreOffice for JDesk
#
# All programs installed by this script are launched THROUGH JDesk's
# governance layer (JVM Memory Proxy), not called directly to the OS.
# The symlinks created here are targets for the memory-guard wrapper,
# not direct user invocations.
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

INSTALL_DIR="${1:-/opt/jdesk/apps/libreoffice}"
MANIFESTS_DIR="/opt/jdesk/manifests"
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${GREEN}[JDesk]${NC} LibreOffice Installer"
echo "  Target: $INSTALL_DIR"
echo "  Size:   ~350 MB"
echo "  Mode:   GOVERNED (all execution via java -memory-guard)"
echo ""

# Check if already installed
if command -v soffice &>/dev/null && [ -L "$INSTALL_DIR/soffice" ]; then
    echo -e "${YELLOW}[SKIP]${NC} LibreOffice already installed."
    soffice --version 2>/dev/null | head -1 || true
    exit 0
fi

# Install via apt
echo -e "${GREEN}[1/4]${NC} Installing LibreOffice Writer, Calc, Impress..."
apt-get update -qq
apt-get install -y --no-install-recommends \
    libreoffice-writer \
    libreoffice-calc \
    libreoffice-impress \
    libreoffice-common

# Create directory and symlinks (these are targets for memory-guard, not direct use)
echo -e "${GREEN}[2/4]${NC} Creating governed binary references..."
mkdir -p "$INSTALL_DIR"
ln -sf /usr/bin/soffice "$INSTALL_DIR/soffice"
ln -sf /usr/bin/lowriter "$INSTALL_DIR/lowriter"
ln -sf /usr/bin/localc "$INSTALL_DIR/localc"
ln -sf /usr/bin/loimpress "$INSTALL_DIR/loimpress"

# Create JDesk application manifest (enforces memory-guard governance)
echo -e "${GREEN}[3/4]${NC} Registering with JDesk governance..."
mkdir -p "$MANIFESTS_DIR"
cat > "$MANIFESTS_DIR/writer.jdesk-app" << EOF
# JDesk Application Manifest: LibreOffice Writer
# ALL execution goes through java -memory-guard
name=Writer
binary=$INSTALL_DIR/soffice
args=--writer
icon=/opt/jdesk/icons/writer.svg
profile=writer
category=office
desktop=true
panel=true
ram-soft=512m
ram-hard=2g
cpu=80
threads=32
disk-write=200m
EOF

cat > "$MANIFESTS_DIR/calc.jdesk-app" << EOF
name=Calc
binary=$INSTALL_DIR/soffice
args=--calc
icon=/opt/jdesk/icons/writer.svg
profile=writer
category=office
desktop=false
panel=false
ram-soft=512m
ram-hard=2g
cpu=80
threads=32
EOF

# Verify
echo -e "${GREEN}[4/4]${NC} Verifying..."
if soffice --version &>/dev/null; then
    VERSION=$(soffice --version 2>/dev/null | head -1)
    echo -e "${GREEN}[OK]${NC} LibreOffice installed: $VERSION"
else
    echo -e "${RED}[ERROR]${NC} soffice --version failed"
    exit 1
fi

echo ""
echo "═══════════════════════════════════════════════════"
echo "  ✓ LibreOffice installed to $INSTALL_DIR"
echo "  Manifest: $MANIFESTS_DIR/writer.jdesk-app"
echo ""
echo "  GOVERNANCE: Programs are NOT launched directly."
echo "  JDesk routes all execution through:"
echo "    java -memory-guard -Xguard:profile=writer $INSTALL_DIR/soffice"
echo "  This enforces RAM, CPU, I/O, and thread limits."
echo "═══════════════════════════════════════════════════"
