#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# install-darling.sh — Install Darling (macOS translation layer) for JDesk
#
# Darling provides macOS API compatibility on Linux, similar to Wine for Windows.
# It is experimental but functional for CLI tools and some GUI applications.
#
# Source: github.com/darlinghq/darling
# License: GPL-3.0
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

INSTALL_DIR="${1:-/opt/jdesk/apps}"
DARLING_PREFIX="/opt/jdesk/darling-prefix"
CACHE_DIR="/opt/jdesk/.cache"
GITHUB_REPO="darlinghq/darling"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

echo -e "${GREEN}[JDesk]${NC} Darling Installer (macOS Translation Layer)"
echo "  Target: $INSTALL_DIR"
echo "  Prefix: $DARLING_PREFIX"
echo "  Size:   ~300 MB"
echo "  Status: EXPERIMENTAL"
echo ""

# Check if already installed
if command -v darling &>/dev/null; then
    echo -e "${YELLOW}[SKIP]${NC} Darling already installed."
    darling --version 2>/dev/null || echo "(version check not supported)"
    exit 0
fi

# Architecture check
ARCH=$(uname -m)
if [ "$ARCH" != "x86_64" ]; then
    echo -e "${RED}[ERROR]${NC} Darling requires x86_64. Current arch: $ARCH"
    exit 1
fi

# Check kernel version (Darling needs specific kernel module support)
KERNEL_VER=$(uname -r)
echo "  Kernel: $KERNEL_VER"
echo ""

# Strategy: Try .deb package first, then build from source
INSTALLED=false

# --- Strategy 1: Check for .deb in cache ---
echo -e "${GREEN}[1/4]${NC} Checking for cached Darling package..."
mkdir -p "$CACHE_DIR"
DEB_PATH=$(find "$CACHE_DIR" -name "darling_*.deb" -type f 2>/dev/null | head -1)

if [ -n "$DEB_PATH" ] && [ -f "$DEB_PATH" ]; then
    echo "  Found cached .deb: $DEB_PATH"
    echo "  Installing..."
    apt-get install -y "$DEB_PATH" && INSTALLED=true || true
fi

# --- Strategy 2: Try GitHub release download ---
if [ "$INSTALLED" = false ]; then
    echo -e "${GREEN}[2/4]${NC} Querying GitHub for Darling release..."

    RELEASE_INFO=$(curl -fsSL "https://api.github.com/repos/$GITHUB_REPO/releases/latest" 2>/dev/null || echo "")

    if [ -n "$RELEASE_INFO" ]; then
        # Look for .deb asset
        DEB_URL=$(echo "$RELEASE_INFO" | grep '"browser_download_url"' | grep '\.deb"' | head -1 | sed 's/.*"browser_download_url": "//;s/".*//')

        if [ -n "$DEB_URL" ]; then
            DEB_NAME=$(basename "$DEB_URL")
            echo "  Found release: $DEB_NAME"
            echo "  Downloading..."

            curl -fSL --progress-bar -o "$CACHE_DIR/$DEB_NAME" "$DEB_URL"

            echo "  Installing .deb..."
            apt-get install -y "$CACHE_DIR/$DEB_NAME" && INSTALLED=true || {
                echo -e "  ${YELLOW}[WARN]${NC} .deb install failed. Trying dpkg with dependency fix..."
                dpkg -i "$CACHE_DIR/$DEB_NAME" 2>/dev/null || true
                apt-get install -f -y && INSTALLED=true || true
            }
        else
            echo -e "  ${YELLOW}[WARN]${NC} No .deb found in latest release."
        fi
    else
        echo -e "  ${YELLOW}[WARN]${NC} Could not reach GitHub API."
    fi
fi

# --- Strategy 3: Install build dependencies and build from source ---
if [ "$INSTALLED" = false ]; then
    echo -e "${GREEN}[3/4]${NC} Attempting build from source..."
    echo -e "  ${CYAN}NOTE:${NC} Building Darling from source takes 30-60 minutes and requires ~10 GB disk."
    echo ""

    # Check if user wants to proceed
    if [ -t 0 ]; then
        read -p "  Build Darling from source? [y/N] " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo -e "  ${YELLOW}[SKIP]${NC} Darling build skipped."
            INSTALLED=false
        fi
    fi

    if [ "$INSTALLED" = false ]; then
        # Install build deps
        echo "  Installing build dependencies..."
        apt-get install -y --no-install-recommends \
            cmake clang bison flex pkg-config \
            linux-headers-$(uname -r) \
            libfuse-dev libbsd-dev \
            libelf-dev libcap-dev \
            git 2>/dev/null || {
            echo -e "  ${YELLOW}[WARN]${NC} Some build deps unavailable."
        }

        # Clone and build
        BUILD_DIR="/tmp/darling-build"
        rm -rf "$BUILD_DIR"
        git clone --recursive --depth 1 "https://github.com/$GITHUB_REPO.git" "$BUILD_DIR" 2>/dev/null && {
            cd "$BUILD_DIR"
            mkdir -p build && cd build
            cmake .. -DCMAKE_INSTALL_PREFIX=/usr && make -j"$(nproc)" && make install && INSTALLED=true
            cd /
            rm -rf "$BUILD_DIR"
        } || {
            echo -e "  ${RED}[ERROR]${NC} Build from source failed."
            rm -rf "$BUILD_DIR"
        }
    fi
fi

# --- Setup (if installed) ---
echo -e "${GREEN}[4/4]${NC} Configuring..."

if [ "$INSTALLED" = true ] && command -v darling &>/dev/null; then
    # Create prefix
    mkdir -p "$DARLING_PREFIX"
    mkdir -p "$INSTALL_DIR/runtimes"
    mkdir -p /opt/jdesk/bin

    ln -sf "$(which darling)" "$INSTALL_DIR/runtimes/darling"
    ln -sf "$(which darling)" /opt/jdesk/bin/darling

    # Create a wrapper for JDesk's LibraryLinker
    cat > /opt/jdesk/bin/jdesk-dylibhost << 'WRAPPER'
#!/bin/bash
# JDesk dylib host — loads macOS dynamic libraries via Darling
# Usage: jdesk-dylibhost <library.dylib> [entry_point] [args...]

DYLIB="$1"; shift
ENTRY="${1:-}"; [ -n "$ENTRY" ] && shift

if [ -z "$DYLIB" ]; then
    echo "Usage: jdesk-dylibhost <library.dylib> [entry_point] [args...]"
    exit 1
fi

exec darling shell /usr/local/bin/dyld_load "$DYLIB" "$ENTRY" "$@"
WRAPPER
    chmod +x /opt/jdesk/bin/jdesk-dylibhost

    # Create Darling environment config
    cat > /opt/jdesk/darling.env << EOF
# JDesk Darling Environment Configuration
# Source this before running Darling commands:
#   source /opt/jdesk/darling.env

export DARLING_PREFIX="$DARLING_PREFIX"
EOF

    echo ""
    echo "═══════════════════════════════════════════════════"
    echo "  ✓ Darling installed"
    echo "  Binary:    $(which darling)"
    echo "  Prefix:    $DARLING_PREFIX"
    echo "  DylibHost: /opt/jdesk/bin/jdesk-dylibhost"
    echo "  Config:    /opt/jdesk/darling.env"
    echo ""
    echo "  macOS .dylib files can now be launched from JDesk"
    echo "  via the LibraryLinker co-linking engine."
    echo ""
    echo "  ⚠ NOTE: Darling is EXPERIMENTAL."
    echo "  CLI tools work reliably. GUI apps may not."
    echo "═══════════════════════════════════════════════════"
else
    echo ""
    echo "═══════════════════════════════════════════════════"
    echo -e "  ${YELLOW}⚠ Darling NOT installed${NC}"
    echo ""
    echo "  Darling is not available as a pre-built package"
    echo "  for this system. macOS .dylib execution will be"
    echo "  unavailable until Darling is installed manually."
    echo ""
    echo "  Manual install:"
    echo "    git clone --recursive https://github.com/darlinghq/darling.git"
    echo "    cd darling && mkdir build && cd build"
    echo "    cmake .. && make -j\$(nproc) && sudo make install"
    echo ""
    echo "  Or download a .deb from:"
    echo "    https://github.com/darlinghq/darling/releases"
    echo "  and place it in: $CACHE_DIR/"
    echo "  then re-run this script."
    echo "═══════════════════════════════════════════════════"
    exit 0  # Not a hard error — Darling is optional
fi
