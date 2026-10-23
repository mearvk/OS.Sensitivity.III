#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# install-vscodium.sh — Install VSCodium IDE for JDesk
#
# Downloads from GitHub Releases (VSCodium/vscodium).
# Verifies SHA256 integrity when checksum available.
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

INSTALL_DIR="${1:-/opt/jdesk/apps/vscodium}"
CACHE_DIR="/opt/jdesk/.cache"
GITHUB_REPO="VSCodium/vscodium"
ASSET_PATTERN="VSCodium-linux-x64"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${GREEN}[JDesk]${NC} VSCodium IDE Installer"
echo "  Target: $INSTALL_DIR"
echo "  Source: github.com/$GITHUB_REPO"
echo "  Size:   ~300 MB"
echo ""

# Check if already installed
if [ -x "$INSTALL_DIR/bin/codium" ]; then
    echo -e "${YELLOW}[SKIP]${NC} VSCodium already installed."
    "$INSTALL_DIR/bin/codium" --version 2>/dev/null | head -1 || true
    exit 0
fi

# Ensure dependencies
echo -e "${GREEN}[1/5]${NC} Checking dependencies..."
for cmd in curl tar; do
    if ! command -v "$cmd" &>/dev/null; then
        echo -e "${RED}[ERROR]${NC} Required command not found: $cmd"
        exit 1
    fi
done

# Query latest release
echo -e "${GREEN}[2/5]${NC} Querying latest VSCodium release..."
LATEST_TAG=$(curl -fsSL "https://api.github.com/repos/$GITHUB_REPO/releases/latest" \
    | grep '"tag_name"' | head -1 | sed 's/.*: "//;s/".*//')

if [ -z "$LATEST_TAG" ]; then
    echo -e "${RED}[ERROR]${NC} Could not determine latest release tag."
    echo "  Check internet connection and GitHub API access."
    exit 1
fi
echo "  Latest release: $LATEST_TAG"

# Download
TARBALL="${ASSET_PATTERN}-${LATEST_TAG}.tar.gz"
DOWNLOAD_URL="https://github.com/$GITHUB_REPO/releases/download/$LATEST_TAG/$TARBALL"
SHA_URL="${DOWNLOAD_URL}.sha256"

mkdir -p "$CACHE_DIR"
TARBALL_PATH="$CACHE_DIR/$TARBALL"

if [ -f "$TARBALL_PATH" ]; then
    echo -e "${YELLOW}[CACHE]${NC} Using cached tarball: $TARBALL"
else
    echo -e "${GREEN}[3/5]${NC} Downloading $TARBALL..."
    curl -fSL --progress-bar -o "$TARBALL_PATH" "$DOWNLOAD_URL"
fi

# Verify SHA256 (if available)
echo -e "${GREEN}[4/5]${NC} Verifying integrity..."
SHA_PATH="$CACHE_DIR/${TARBALL}.sha256"
if curl -fsSL -o "$SHA_PATH" "$SHA_URL" 2>/dev/null; then
    EXPECTED=$(awk '{print $1}' "$SHA_PATH")
    ACTUAL=$(sha256sum "$TARBALL_PATH" | awk '{print $1}')
    if [ "$EXPECTED" = "$ACTUAL" ]; then
        echo -e "  ${GREEN}✓${NC} SHA-256 verified"
    else
        echo -e "  ${RED}✗ SHA-256 mismatch!${NC}"
        echo "    Expected: $EXPECTED"
        echo "    Got:      $ACTUAL"
        rm -f "$TARBALL_PATH"
        exit 1
    fi
else
    echo -e "  ${YELLOW}⚠${NC} No checksum available — skipping verification"
fi

# Extract
echo -e "${GREEN}[5/5]${NC} Extracting to $INSTALL_DIR..."
mkdir -p "$INSTALL_DIR"
tar -xzf "$TARBALL_PATH" -C "$INSTALL_DIR" --strip-components=0

# Create convenience symlink
mkdir -p /opt/jdesk/bin
ln -sf "$INSTALL_DIR/bin/codium" /opt/jdesk/bin/codium

# Verify
if [ -x "$INSTALL_DIR/bin/codium" ]; then
    VERSION=$("$INSTALL_DIR/bin/codium" --version 2>/dev/null | head -1 || echo "$LATEST_TAG")
    echo ""
    echo "═══════════════════════════════════════════════════"
    echo "  ✓ VSCodium installed: $VERSION"
    echo "  Binary:  $INSTALL_DIR/bin/codium"
    echo "  Symlink: /opt/jdesk/bin/codium"
    echo "  Profile: ide"
    echo "═══════════════════════════════════════════════════"
else
    echo -e "${RED}[ERROR]${NC} Installation failed — binary not executable"
    exit 1
fi
