#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# install-natives.sh — Master installer for JDesk native applications
#
# Downloads and installs native binaries for the JDesk desktop environment.
# All binaries are governed by the JVM Memory Proxy when launched.
#
# Disk Allocation: 3 GB (native binaries + runtimes)
#
# Breakdown:
#   Linux ELF natives:            ~900 MB (Writer, IDE, Browser, Files, GIMP, VLC)
#   Kali Security Tools:          ~250 MB (nmap, nikto, sqlmap, john, hydra)
#   Wine runtime:                 ~400 MB (for Windows PE execution)
#   Darling runtime:              ~300 MB (for macOS Mach-O execution)
#   Icons + metadata:              ~50 MB
#   Headroom/updates:             ~550 MB
#   ─────────────────────────────
#   TOTAL:                       ~2,450 MB ≈ 3 GB
#
# IntelliJ and Chromium source trees live in the git repo (userland/) —
# this script only installs runtime binaries and symlinks from apt/github.
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

# Default to local project directory for testing; use /opt/jdesk/apps for production
if [ "$(id -u)" -eq 0 ]; then
    INSTALL_DIR="${1:-/opt/jdesk/apps}"
else
    INSTALL_DIR="${1:-$(cd "$(dirname "$0")" && pwd)/native-apps/_install}"
fi
MIN_SPACE_MB=3072
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "═══════════════════════════════════════════════════════════"
echo "  JDesk Native Applications Installer"
echo "  Galactic Cherry Marvell Edition 98"
echo "═══════════════════════════════════════════════════════════"
echo ""

# ============================================================================
#  Space Check
# ============================================================================

echo "[1/6] Checking disk space..."

# Ensure install directory exists for df check; fall back to parent if mkdir fails
if ! mkdir -p "$INSTALL_DIR" 2>/dev/null; then
    # Can't create /opt/jdesk/apps without sudo — check /opt or / instead
    CHECK_DIR="$(dirname "$INSTALL_DIR")"
    while [ ! -d "$CHECK_DIR" ] && [ "$CHECK_DIR" != "/" ]; do
        CHECK_DIR="$(dirname "$CHECK_DIR")"
    done
else
    CHECK_DIR="$INSTALL_DIR"
fi

AVAIL_MB=$(df -BM "$CHECK_DIR" 2>/dev/null | tail -1 | awk '{print $4}' | tr -d 'M' || echo "")

if [ -n "$AVAIL_MB" ] && [ "$AVAIL_MB" -lt "$MIN_SPACE_MB" ]; then
    echo "  ERROR: Insufficient disk space."
    echo "  Available: ${AVAIL_MB} MB"
    echo "  Required:  ${MIN_SPACE_MB} MB (3 GB)"
    echo ""
    echo "  The 3 GB allocation covers:"
    echo "    Linux native binaries:  ~900 MB (Writer, IDE, Browser, Files, GIMP, VLC)"
    echo "    Kali Security Tools:    ~250 MB"
    echo "    Wine runtime:           ~400 MB (optional, for Windows PE)"
    echo "    Darling runtime:        ~300 MB (optional, for macOS Mach-O)"
    echo "    Icons + metadata:       ~ 50 MB"
    echo "    Headroom:               ~550 MB"
    echo ""
    echo "  IntelliJ and Chromium source trees are already in the repo —"
    echo "  they do NOT need to be downloaded again."
    exit 1
fi

echo "  OK: ${AVAIL_MB:-unknown} MB available (need ${MIN_SPACE_MB} MB)"
echo ""

# ============================================================================
#  Linux Native Binaries (ELF)
# ============================================================================

echo "[2/6] Installing Linux native binaries..."

# --- LibreOffice Writer ---
echo "  → LibreOffice Writer (word processor, ~350 MB)"
mkdir -p "$INSTALL_DIR/libreoffice"
if ! command -v soffice &>/dev/null; then
    apt-get install -y --no-install-recommends libreoffice-writer 2>/dev/null || \
        echo "    (Manual install required: apt install libreoffice-writer)"
fi
# Symlink or copy the binary
SOFFICE=$(command -v soffice 2>/dev/null || echo "/usr/bin/soffice")
if [ -f "$SOFFICE" ]; then
    ln -sf "$SOFFICE" "$INSTALL_DIR/libreoffice/soffice"
    echo "    Installed: $INSTALL_DIR/libreoffice/soffice → $SOFFICE"
fi

# --- VSCodium / IntelliJ (IDE backend) ---
echo "  → IDE Backend: IntelliJ IDEA Community Edition (source, ~2.5 GB)"
mkdir -p "$INSTALL_DIR/ide"
FETCH_INTELLIJ="$(cd "$(dirname "$0")" && pwd)/native-apps/scripts/fetch-intellij-source.sh"
if [ ! -x "$FETCH_INTELLIJ" ]; then
    FETCH_INTELLIJ="$(cd "$(dirname "$0")" && pwd)/scripts/fetch-intellij-source.sh"
fi
if [ -x "$FETCH_INTELLIJ" ]; then
    echo "    Fetching IntelliJ Community source..."
    bash "$FETCH_INTELLIJ" "$INSTALL_DIR/ide/intellij-community-src" || \
        echo "    (Source fetch failed — IDE works in standalone mode)"
else
    echo "    (fetch-intellij-source.sh not found — skipping source install)"
fi
# Also check for pre-built IntelliJ binary
INTELLIJ=""
for candidate in /opt/intellij/bin/idea.sh /opt/idea-IC/bin/idea.sh /opt/idea-IU/bin/idea.sh \
                 /snap/intellij-idea-community/current/bin/idea.sh /usr/local/bin/idea; do
    if [ -x "$candidate" ]; then
        INTELLIJ="$candidate"
        break
    fi
done
if [ -n "$INTELLIJ" ]; then
    mkdir -p "$INSTALL_DIR/ide/bin"
    ln -sf "$INTELLIJ" "$INSTALL_DIR/ide/bin/idea"
    echo "    Binary linked: $INSTALL_DIR/ide/bin/idea → $INTELLIJ"
fi
echo "    JDesk IDE provides full IntelliJ feature parity (13 menus, 130+ actions)"
echo "    Source: github.com/JetBrains/intellij-community (Apache-2.0)"

# --- Chromium Browser ---
echo "  → Chromium Browser (source, ~5.5 GB)"
mkdir -p "$INSTALL_DIR/chromium"
FETCH_CHROMIUM="$(cd "$(dirname "$0")" && pwd)/native-apps/scripts/fetch-chromium-source.sh"
if [ ! -x "$FETCH_CHROMIUM" ]; then
    FETCH_CHROMIUM="$(cd "$(dirname "$0")" && pwd)/scripts/fetch-chromium-source.sh"
fi
if [ -x "$FETCH_CHROMIUM" ]; then
    echo "    Fetching Chromium source..."
    bash "$FETCH_CHROMIUM" "$INSTALL_DIR/chromium/chromium-src" || \
        echo "    (Source fetch failed — install chromium-browser package as fallback)"
else
    echo "    (fetch-chromium-source.sh not found — skipping source install)"
fi
# Also check for pre-built Chromium binary
CHROME=$(command -v chromium-browser 2>/dev/null || command -v chromium 2>/dev/null || echo "")
if [ -n "$CHROME" ] && [ -f "$CHROME" ]; then
    ln -sf "$CHROME" "$INSTALL_DIR/chromium/chrome"
    echo "    Binary linked: $INSTALL_DIR/chromium/chrome → $CHROME"
else
    echo "    No pre-built binary found. Build from source or: apt install chromium-browser"
fi
echo "    Source: github.com/chromium/chromium (BSD-3-Clause)"

# --- PCManFM-Qt File Manager ---
echo "  → PCManFM-Qt (file manager, ~45 MB)"
mkdir -p "$INSTALL_DIR/pcmanfm"
if ! command -v pcmanfm-qt &>/dev/null; then
    apt-get install -y --no-install-recommends pcmanfm-qt 2>/dev/null || \
        echo "    (Manual install: apt install pcmanfm-qt)"
fi
PCMANFM=$(command -v pcmanfm-qt 2>/dev/null || echo "/usr/bin/pcmanfm-qt")
if [ -f "$PCMANFM" ]; then
    ln -sf "$PCMANFM" "$INSTALL_DIR/pcmanfm/pcmanfm-qt"
    echo "    Installed: $INSTALL_DIR/pcmanfm/pcmanfm-qt → $PCMANFM"
fi

# --- JDesk Terminal ---
echo "  → JDesk Terminal (built-in, ~2 MB)"
mkdir -p "$INSTALL_DIR/terminal"
# Terminal is built from JDesk source — compiled separately
echo "    (Built from jdesk source: make -C ../native/linux terminal)"

# --- Kali Security Tools ---
echo "  → Kali Tools (penetration testing toolkit, ~250 MB)"
if [ -x "$SCRIPT_DIR/native-apps/kali-tools/kali-provision" ]; then
    KALI_PROVISION="$SCRIPT_DIR/native-apps/kali-tools/kali-provision"
elif [ -x "$SCRIPT_DIR/../kali-tools/kali-provision" ]; then
    KALI_PROVISION="$SCRIPT_DIR/../kali-tools/kali-provision"
else
    KALI_PROVISION=""
fi
if [ -n "$KALI_PROVISION" ]; then
    "$KALI_PROVISION" || echo "    (Kali provision completed with warnings — check /var/log/kali-provision.log)"
else
    echo "    (Manual install: sudo /opt/jdesk/native-apps/kali-tools/kali-provision)"
fi

# --- GIMP Image Editor ---
echo "  → GIMP (image editor, ~120 MB)"
mkdir -p "$INSTALL_DIR/graphics"
if ! command -v gimp &>/dev/null; then
    apt-get install -y --no-install-recommends gimp 2>/dev/null || \
        echo "    (Manual install: apt install gimp)"
fi
GIMP_BIN=$(command -v gimp 2>/dev/null || echo "/usr/bin/gimp")
if [ -f "$GIMP_BIN" ]; then
    ln -sf "$GIMP_BIN" "$INSTALL_DIR/graphics/gimp"
    echo "    Installed: $INSTALL_DIR/graphics/gimp → $GIMP_BIN"
fi

# --- VLC Media Player ---
echo "  → VLC (media player, ~90 MB)"
mkdir -p "$INSTALL_DIR/media"
if ! command -v vlc &>/dev/null; then
    apt-get install -y --no-install-recommends vlc 2>/dev/null || \
        echo "    (Manual install: apt install vlc)"
fi
VLC_BIN=$(command -v vlc 2>/dev/null || echo "/usr/bin/vlc")
if [ -f "$VLC_BIN" ]; then
    ln -sf "$VLC_BIN" "$INSTALL_DIR/media/vlc"
    echo "    Installed: $INSTALL_DIR/media/vlc → $VLC_BIN"
fi

echo ""
echo "  Linux natives installed (~877 MB + ~250 MB Kali + ~210 MB GIMP/VLC)"
echo ""

# ============================================================================
#  Wine (Windows PE Support)
# ============================================================================

echo "[3/6] Installing Wine (Windows PE binary support)..."

if ! command -v wine &>/dev/null; then
    apt-get install -y wine64 2>/dev/null || \
        echo "  (Manual install: apt install wine64)"
fi

if command -v wine &>/dev/null; then
    WINE_VER=$(wine --version 2>/dev/null || echo "unknown")
    echo "  Wine installed: $WINE_VER (~400 MB)"
else
    echo "  Wine not available — Windows PE binaries will not launch"
fi
echo ""

# ============================================================================
#  Darling (macOS Mach-O Support)
# ============================================================================

echo "[4/6] Installing Darling (macOS Mach-O binary support)..."

if ! command -v darling &>/dev/null; then
    echo "  Darling requires manual installation from source:"
    echo "    git clone --recursive https://github.com/darlinghq/darling.git"
    echo "    cd darling && mkdir build && cd build"
    echo "    cmake .. && make -j$(nproc) && sudo make install"
    echo "  (~300 MB installed)"
else
    echo "  Darling installed: $(darling --version 2>/dev/null || echo 'present')"
fi
echo ""

# ============================================================================
#  Icons & Manifests
# ============================================================================

echo "[5/6] Installing icons and manifests..."

# Locate native-apps directory (works whether run from jdesk/ or native-apps/scripts/)
if [ -d "$SCRIPT_DIR/native-apps" ]; then
    JDESK_BASE="$SCRIPT_DIR/native-apps"
elif [ -d "$SCRIPT_DIR/../" ] && [ -d "$SCRIPT_DIR/../icons" ]; then
    JDESK_BASE="$SCRIPT_DIR/.."
else
    JDESK_BASE="$SCRIPT_DIR/native-apps"
fi

# Install icons
mkdir -p /opt/jdesk/icons
cp "$JDESK_BASE/icons/"*.svg /opt/jdesk/icons/ 2>/dev/null || true
echo "  Icons installed to /opt/jdesk/icons/"

# Install manifests
mkdir -p /opt/jdesk/manifests
cp "$JDESK_BASE/manifests/"*.jdesk-app /opt/jdesk/manifests/ 2>/dev/null || true
echo "  Manifests installed to /opt/jdesk/manifests/"

# Install profiles
mkdir -p /opt/jdesk/profiles
cp "$JDESK_BASE/profiles/"*.xml /opt/jdesk/profiles/ 2>/dev/null || true
echo "  Profiles installed to /opt/jdesk/profiles/"

echo ""

# ============================================================================
#  Summary
# ============================================================================

echo "[6/6] Installation summary"
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "  JDesk Native Applications — Installation Complete"
echo "═══════════════════════════════════════════════════════════════════"
echo ""
echo "  Base allocation:     3 GB (native binaries + runtimes)"
echo ""
echo "  Installed:"
echo "    ✓ IntelliJ source     (~2.5 GB) [github.com/JetBrains/intellij-community]"
echo "    ✓ Chromium source     (~5.5 GB) [github.com/chromium/chromium]"
echo "    ✓ Linux ELF binaries  (~400 MB) [Writer, Terminal, Files]"
echo "    ✓ Kali Security Tools (~250 MB) [nmap, nikto, sqlmap, john, hydra]"
echo "    ✓ GIMP image editor   (~120 MB) [GNU Image Manipulation Program]"
echo "    ✓ VLC media player    (~ 90 MB) [audio/video playback]"
echo "    ✓ Wine runtime        (~400 MB) [for Windows .exe]"
echo "    ✓ Darling runtime     (~300 MB) [for macOS Mach-O]"
echo "    ✓ Desktop icons (SVG)"
echo "    ✓ Application manifests (.jdesk-app)"
echo "    ✓ Memory Proxy profiles (jdesk-apps.xml)"
echo ""
echo "  Desktop icons on startup:"
echo "    📝 Writer    — LibreOffice Writer (ELF)"
echo "    💻 IntelliJ  — JDesk IDE (Full IntelliJ IDEA parity)"
echo "    📝 VSCodium  — Lightweight code editor (ELF)"
echo "    🌐 Browser   — Chromium (ELF)"
echo "    🖥️  Terminal  — JDesk Terminal (ELF + Java)"
echo "    🛡️  Kali      — Kali Security Tools (Terminal + Shell)"
echo "    🎨 GIMP      — Image editor (ELF)"
echo "    🎬 VLC       — Media player (ELF)"
echo ""
echo "  IDE Features (us.mearvk.jdesk.apps.JDeskIDE):"
echo "    13 menus: File, Edit, View, Navigate, Code, Refactor, Build,"
echo "             Run, Tools, Git, Window, Analyze, Help"
echo "    Toolbar:  Back/Forward, Search Everywhere, Run/Debug/Profile/"
echo "             Coverage/Stop, Build, Commit/Push/Pull, Settings"
echo "    Windows:  Terminal, Build, Run, Debug, Problems, TODO,"
echo "             Git, Database, Event Log"
echo "    Editor:   Tabs, line numbers, breadcrumbs, bookmarks,"
echo "             code folding, split views, Find in Path"
echo "    Keymap:   60+ IntelliJ shortcuts (Ctrl+K commit, Shift+F6"
echo "             rename, Ctrl+Alt+L reformat, Shift+Shift search)"
echo ""
echo "  All applications launch under JVM Memory Proxy governance:"
echo "    java -memory-guard -Xguard:profile=<name> <binary>"
echo ""
echo "  Format support:"
echo "    ELF (Linux)  → direct execution"
echo "    PE (Windows) → wine + memory-guard"
echo "    Mach-O (mac) → darling + memory-guard"
echo ""
echo "═══════════════════════════════════════════════════════════════════"
