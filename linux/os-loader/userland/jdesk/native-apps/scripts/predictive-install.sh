#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# predictive-install.sh — Predictive Native Installer for JDesk
#
# Installs JDesk native applications using a 4-tier priority model that
# considers available disk space, ranks programs by size/utility/probable use,
# scans existing natives for version/SHA/installer ID, and supports
# delete/check-ahead before installing replacements.
#
# Predictive Ordering (1-4):
#   Priority 1 (Essential):   Terminal, Files, coreutils — always installed first
#   Priority 2 (Productive):  IDE, Writer, Git — high utility, moderate size
#   Priority 3 (Connected):   Browser, SSH, curl — internet/network facing
#   Priority 4 (Extended):    Wine, Darling, GIMP, VLC, Kali — large, optional
#
# The installer:
#   1. Scans available disk space on target filesystem
#   2. Checks existing natives: version, SHA-256, installer ID, install date
#   3. Orders the install queue by (priority, utility/size ratio, probable use)
#   4. Installs in order, stopping gracefully if space runs out
#   5. Supports --delete, --check, --force-reinstall for existing natives
#
# Usage:
#   sudo predictive-install.sh                 # Install what fits, priority order
#   sudo predictive-install.sh --check         # Check existing natives (no install)
#   sudo predictive-install.sh --delete <id>   # Remove a native by ID
#   sudo predictive-install.sh --plan          # Show what would be installed (dry run)
#   sudo predictive-install.sh --force <id>    # Reinstall even if current version matches
#   sudo predictive-install.sh --tier 1-2      # Only install tiers 1 and 2
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

# ===========================================================================
#  Configuration
# ===========================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE_APPS_DIR="$(dirname "$SCRIPT_DIR")"
INSTALL_DIR="/opt/jdesk/apps"
REGISTRY_DIR="/opt/jdesk/.registry"
REGISTRY_FILE="$REGISTRY_DIR/natives.json"
LOG_FILE="/var/log/jdesk-predictive-install.log"

# Fall back to user-writable log if not root
if [ "$(id -u)" -ne 0 ]; then
    LOG_FILE="${HOME}/.local/share/jdesk-predictive-install.log"
    mkdir -p "$(dirname "$LOG_FILE")" 2>/dev/null || LOG_FILE="/dev/null"
fi

# Installer identity
INSTALLER_ID="mearvk-installer-tech-2"
INSTALLER_VERSION="1.0.0"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ===========================================================================
#  Native Application Registry
#
#  Each entry: id, name, size_mb, utility (1-10), probable_use (1-10),
#              priority_tier (1-4), source, binary_path, apt_package/github
#
#  Predictive score = utility × probable_use / size_mb
#  Within each tier, higher score installs first.
# ===========================================================================

# Predictive Tier 1: Essential (always first — small, always used)
declare -A TIER1_APPS=(
    [terminal]="JDesk Terminal|2|10|10|builtin|/opt/jdesk/apps/terminal/bash|bash"
    [files]="PCManFM-Qt|45|9|9|apt|/opt/jdesk/apps/pcmanfm/pcmanfm-qt|pcmanfm-qt"
    [coreutils]="GNU Coreutils|15|10|10|apt|/opt/jdesk/apps/coreutils/ls|coreutils"
    [ssh]="OpenSSH Client|5|8|8|apt|/opt/jdesk/apps/network/ssh|openssh-client"
)

# Predictive Tier 2: Productive (high utility, moderate size)
declare -A TIER2_APPS=(
    [ide]="VSCodium IDE|300|10|8|github|/opt/jdesk/apps/vscodium/bin/codium|VSCodium/vscodium"
    [writer]="LibreOffice Writer|350|8|7|apt|/opt/jdesk/apps/libreoffice/soffice|libreoffice-writer"
    [git]="Git|40|9|9|apt|/opt/jdesk/apps/development/git|git"
    [curl]="cURL|3|7|8|apt|/opt/jdesk/apps/network/curl|curl"
    [telnet]="Telnet|1|5|5|apt|/opt/jdesk/apps/network/telnet|telnet"
)

# Predictive Tier 3: Connected (network/internet facing)
declare -A TIER3_APPS=(
    [browser]="Chromium|180|9|8|apt|/opt/jdesk/apps/chromium/chrome|chromium-browser"
)

# Predictive Tier 4: Extended (large footprint, optional functionality)
declare -A TIER4_APPS=(
    [wine]="Wine Runtime|400|6|4|apt|/opt/jdesk/apps/runtimes/wine|wine64"
    [darling]="Darling macOS|300|5|3|manual|/opt/jdesk/apps/runtimes/darling|darling"
    [gimp]="GIMP|120|7|4|apt|/opt/jdesk/apps/graphics/gimp|gimp"
    [vlc]="VLC Media|90|7|5|apt|/opt/jdesk/apps/media/vlc|vlc"
    [kali]="Kali Tools|250|6|3|script|/opt/jdesk/apps/kali/nmap|kali-provision"
)

# ===========================================================================
#  Functions
# ===========================================================================

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"; }
info()    { echo -e "  ${BLUE}[INFO]${NC}  $1"; log "[INFO] $1"; }
success() { echo -e "  ${GREEN}[OK]${NC}    $1"; log "[OK] $1"; }
warn()    { echo -e "  ${YELLOW}[WARN]${NC}  $1"; log "[WARN] $1"; }
error()   { echo -e "  ${RED}[FAIL]${NC}  $1"; log "[FAIL] $1"; }

# ---------------------------------------------------------------------------
#  Disk Space Scanner
# ---------------------------------------------------------------------------

scan_available_space() {
    mkdir -p "$INSTALL_DIR" 2>/dev/null || true
    local avail_mb
    avail_mb=$(df -BM "$INSTALL_DIR" 2>/dev/null | tail -1 | awk '{print $4}' | tr -d 'M')
    echo "$avail_mb"
}

scan_total_space() {
    local total_mb
    total_mb=$(df -BM "$INSTALL_DIR" 2>/dev/null | tail -1 | awk '{print $2}' | tr -d 'M')
    echo "$total_mb"
}

scan_used_by_jdesk() {
    if [ -d "$INSTALL_DIR" ]; then
        du -sm "$INSTALL_DIR" 2>/dev/null | awk '{print $1}'
    else
        echo "0"
    fi
}

# ---------------------------------------------------------------------------
#  SHA-256 Computation
# ---------------------------------------------------------------------------

compute_sha256() {
    local path="$1"
    if [ -f "$path" ]; then
        sha256sum "$path" 2>/dev/null | awk '{print $1}'
    elif [ -L "$path" ]; then
        local target
        target=$(readlink -f "$path")
        if [ -f "$target" ]; then
            sha256sum "$target" 2>/dev/null | awk '{print $1}'
        else
            echo "BROKEN_SYMLINK"
        fi
    else
        echo "NOT_FOUND"
    fi
}

# ---------------------------------------------------------------------------
#  Version Detection
# ---------------------------------------------------------------------------

detect_version() {
    local binary="$1"
    local id="$2"

    # Resolve symlinks
    local resolved=""
    if [ -L "$binary" ]; then
        resolved=$(readlink -f "$binary")
    elif [ -f "$binary" ]; then
        resolved="$binary"
    else
        echo "not_installed"
        return
    fi

    [ ! -f "$resolved" ] && { echo "broken"; return; }

    # Try common version flags
    local ver=""
    case "$id" in
        terminal|coreutils)
            ver=$("$resolved" --version 2>/dev/null | head -1 || echo "")
            ;;
        browser)
            ver=$("$resolved" --version 2>/dev/null | head -1 || echo "")
            ;;
        ide)
            ver=$("$resolved" --version 2>/dev/null | head -1 || echo "")
            ;;
        git)
            ver=$(git --version 2>/dev/null || echo "")
            ;;
        writer)
            ver=$(soffice --version 2>/dev/null || echo "")
            ;;
        wine)
            ver=$(wine --version 2>/dev/null || echo "")
            ;;
        ssh)
            ver=$(ssh -V 2>&1 | head -1 || echo "")
            ;;
        curl)
            ver=$(curl --version 2>/dev/null | head -1 || echo "")
            ;;
        *)
            ver=$("$resolved" --version 2>/dev/null | head -1 || echo "unknown")
            ;;
    esac

    [ -z "$ver" ] && ver="installed (version unknown)"
    echo "$ver"
}

# ---------------------------------------------------------------------------
#  Registry: Read/Write native metadata
# ---------------------------------------------------------------------------

init_registry() {
    mkdir -p "$REGISTRY_DIR"
    if [ ! -f "$REGISTRY_FILE" ]; then
        cat > "$REGISTRY_FILE" << 'ENDJSON'
{
  "schema_version": 1,
  "installer_id": "",
  "created": "",
  "natives": {}
}
ENDJSON
    fi
}

registry_get_entry() {
    local id="$1"
    jq -r ".natives[\"$id\"] // empty" "$REGISTRY_FILE" 2>/dev/null
}

registry_set_entry() {
    local id="$1"
    local name="$2"
    local version="$3"
    local sha256="$4"
    local size_mb="$5"
    local binary="$6"
    local tier="$7"

    local tmp
    tmp=$(mktemp)
    jq --arg id "$id" \
       --arg name "$name" \
       --arg ver "$version" \
       --arg sha "$sha256" \
       --arg sz "$size_mb" \
       --arg bin "$binary" \
       --arg tier "$tier" \
       --arg installer "$INSTALLER_ID" \
       --arg date "$(date -Iseconds)" \
       '.natives[$id] = {
           "name": $name,
           "version": $ver,
           "sha256": $sha,
           "size_mb": ($sz | tonumber),
           "binary": $bin,
           "tier": ($tier | tonumber),
           "installer_id": $installer,
           "install_date": $date,
           "install_count": ((.natives[$id].install_count // 0) + 1)
       }' "$REGISTRY_FILE" > "$tmp" && mv "$tmp" "$REGISTRY_FILE"
}

registry_remove_entry() {
    local id="$1"
    local tmp
    tmp=$(mktemp)
    jq --arg id "$id" 'del(.natives[$id])' "$REGISTRY_FILE" > "$tmp" && mv "$tmp" "$REGISTRY_FILE"
}

# ---------------------------------------------------------------------------
#  Check Existing Natives
# ---------------------------------------------------------------------------

check_native() {
    local id="$1"
    local name="$2"
    local binary="$3"

    local version sha256 reg_entry reg_sha reg_ver reg_installer reg_date

    version=$(detect_version "$binary" "$id")
    sha256=$(compute_sha256 "$binary")

    # Check registry
    reg_entry=$(registry_get_entry "$id")

    if [ -n "$reg_entry" ]; then
        reg_sha=$(echo "$reg_entry" | jq -r '.sha256 // "none"')
        reg_ver=$(echo "$reg_entry" | jq -r '.version // "none"')
        reg_installer=$(echo "$reg_entry" | jq -r '.installer_id // "unknown"')
        reg_date=$(echo "$reg_entry" | jq -r '.install_date // "unknown"')
    else
        reg_sha="unregistered"
        reg_ver="unregistered"
        reg_installer="unregistered"
        reg_date="unregistered"
    fi

    echo -e "    ${BOLD}$name${NC} (id: $id)"
    echo -e "      Binary:       $binary"
    echo -e "      Version:      $version"
    echo -e "      SHA-256:      ${sha256:0:16}..."
    echo -e "      Registered:   ver=$reg_ver  installer=$reg_installer"
    echo -e "      Install date: $reg_date"

    # SHA mismatch detection
    if [ "$reg_sha" != "unregistered" ] && [ "$sha256" != "NOT_FOUND" ] && [ "$sha256" != "$reg_sha" ]; then
        echo -e "      ${YELLOW}⚠ SHA MISMATCH — binary changed since last install${NC}"
        echo -e "      ${YELLOW}  Registry: ${reg_sha:0:16}...  Actual: ${sha256:0:16}...${NC}"
    fi

    echo ""
}

check_all_natives() {
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  JDesk Native Check — Version / SHA-256 / Installer ID"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""

    echo -e "  ${CYAN}── Tier 1: Essential ──${NC}"
    for id in "${!TIER1_APPS[@]}"; do
        IFS='|' read -r name size_mb utility probable source binary pkg <<< "${TIER1_APPS[$id]}"
        check_native "$id" "$name" "$binary"
    done

    echo -e "  ${CYAN}── Tier 2: Productive ──${NC}"
    for id in "${!TIER2_APPS[@]}"; do
        IFS='|' read -r name size_mb utility probable source binary pkg <<< "${TIER2_APPS[$id]}"
        check_native "$id" "$name" "$binary"
    done

    echo -e "  ${CYAN}── Tier 3: Connected ──${NC}"
    for id in "${!TIER3_APPS[@]}"; do
        IFS='|' read -r name size_mb utility probable source binary pkg <<< "${TIER3_APPS[$id]}"
        check_native "$id" "$name" "$binary"
    done

    echo -e "  ${CYAN}── Tier 4: Extended ──${NC}"
    for id in "${!TIER4_APPS[@]}"; do
        IFS='|' read -r name size_mb utility probable source binary pkg <<< "${TIER4_APPS[$id]}"
        check_native "$id" "$name" "$binary"
    done

    echo "═══════════════════════════════════════════════════════════════"
}

# ---------------------------------------------------------------------------
#  Delete Native
# ---------------------------------------------------------------------------

delete_native() {
    local target_id="$1"
    local found=0

    # Search all tiers for the ID
    for tier_name in TIER1_APPS TIER2_APPS TIER3_APPS TIER4_APPS; do
        declare -n tier_ref="$tier_name"
        if [ -n "${tier_ref[$target_id]+x}" ]; then
            IFS='|' read -r name size_mb utility probable source binary pkg <<< "${tier_ref[$target_id]}"
            found=1

            echo ""
            echo -e "  ${YELLOW}Removing native: $name (id: $target_id)${NC}"

            # Remove the symlink/binary at install location
            if [ -L "$binary" ]; then
                rm -f "$binary"
                success "Removed symlink: $binary"
            elif [ -f "$binary" ]; then
                warn "Binary is not a symlink — removing directory"
                local dir
                dir=$(dirname "$binary")
                if [[ "$dir" == /opt/jdesk/apps/* ]]; then
                    rm -rf "$dir"
                    success "Removed directory: $dir"
                else
                    error "Refusing to remove outside /opt/jdesk/apps/: $dir"
                fi
            else
                info "Binary not found at $binary (already removed?)"
            fi

            # Remove from registry
            registry_remove_entry "$target_id"
            success "Removed from registry"
            break
        fi
    done

    if [ "$found" -eq 0 ]; then
        error "Unknown native ID: $target_id"
        echo "  Valid IDs: terminal, files, coreutils, ssh, ide, writer, git, curl,"
        echo "             telnet, browser, wine, darling, gimp, vlc, kali"
        return 1
    fi
}

# ---------------------------------------------------------------------------
#  Predictive Install — Compute order and install within available space
# ---------------------------------------------------------------------------

compute_predictive_score() {
    local utility="$1"
    local probable_use="$2"
    local size_mb="$3"

    # Score = (utility × probable_use × 100) / size_mb
    # Higher score = better value per MB
    echo $(( (utility * probable_use * 100) / (size_mb + 1) ))
}

install_single_native() {
    local id="$1"
    local name="$2"
    local size_mb="$3"
    local source="$4"
    local binary="$5"
    local pkg="$6"
    local tier="$7"
    local force="${8:-0}"

    # Check if already installed and up to date
    if [ "$force" -eq 0 ]; then
        local existing_sha
        existing_sha=$(compute_sha256 "$binary")
        if [ "$existing_sha" != "NOT_FOUND" ] && [ "$existing_sha" != "BROKEN_SYMLINK" ]; then
            # Already installed — check registry for version match
            local reg_entry
            reg_entry=$(registry_get_entry "$id")
            if [ -n "$reg_entry" ]; then
                success "$name — already installed (skipping)"
                return 0
            fi
        fi
    fi

    case "$source" in
        apt)
            local actual_binary
            actual_binary=$(command -v "$(basename "$pkg")" 2>/dev/null || command -v "$pkg" 2>/dev/null || echo "")

            if [ -z "$actual_binary" ] || [ ! -f "$actual_binary" ]; then
                info "Installing $name via apt ($pkg)..."
                if apt-get install -y -qq "$pkg" 2>/dev/null; then
                    actual_binary=$(command -v "$(basename "$pkg")" 2>/dev/null || dpkg -L "$pkg" 2>/dev/null | grep '/usr/bin/' | head -1 || echo "")
                else
                    error "$name: apt install failed"
                    return 1
                fi
            fi

            # Determine actual binary location
            if [ -z "$actual_binary" ] || [ ! -f "$actual_binary" ]; then
                # Try well-known paths
                for candidate in "/usr/bin/$pkg" "/usr/bin/$(echo "$pkg" | cut -d- -f1)" "/usr/sbin/$pkg"; do
                    if [ -f "$candidate" ]; then
                        actual_binary="$candidate"
                        break
                    fi
                done
            fi

            if [ -n "$actual_binary" ] && [ -f "$actual_binary" ]; then
                local symlink_dir
                symlink_dir=$(dirname "$binary")
                mkdir -p "$symlink_dir"
                ln -sf "$actual_binary" "$binary"

                # Register
                local ver sha
                ver=$(detect_version "$binary" "$id")
                sha=$(compute_sha256 "$binary")
                registry_set_entry "$id" "$name" "$ver" "$sha" "$size_mb" "$binary" "$tier"
                success "$name installed: $binary → $actual_binary"
            else
                error "$name: could not locate binary after install"
                return 1
            fi
            ;;

        github)
            info "Installing $name from GitHub ($pkg)..."
            local api_url="https://api.github.com/repos/$pkg/releases/latest"
            local download_url extract_dir

            extract_dir=$(dirname "$binary")
            mkdir -p "$extract_dir"

            download_url=$(curl -sL "$api_url" 2>/dev/null | \
                jq -r '.assets[] | select(.name | test("linux.*x64.*tar")) | .browser_download_url' | head -1)

            if [ -n "$download_url" ] && [ "$download_url" != "null" ]; then
                local cache_dir="/opt/jdesk/.cache"
                mkdir -p "$cache_dir"
                local filename
                filename=$(basename "$download_url")

                curl -sL -o "$cache_dir/$filename" "$download_url"
                tar xf "$cache_dir/$filename" -C "$extract_dir" --strip-components=0 2>/dev/null || \
                    tar xf "$cache_dir/$filename" -C "$extract_dir" 2>/dev/null

                if [ -f "$binary" ]; then
                    chmod +x "$binary"
                    local ver sha
                    ver=$(detect_version "$binary" "$id")
                    sha=$(compute_sha256 "$binary")
                    registry_set_entry "$id" "$name" "$ver" "$sha" "$size_mb" "$binary" "$tier"
                    success "$name installed from GitHub"
                else
                    error "$name: binary not found after extraction"
                    return 1
                fi
            else
                warn "$name: GitHub API unavailable — skipping"
                return 1
            fi
            ;;

        builtin)
            # Symlink to system binary
            local sys_binary
            sys_binary=$(command -v "$pkg" 2>/dev/null || echo "/usr/bin/$pkg")
            if [ -f "$sys_binary" ]; then
                local symlink_dir
                symlink_dir=$(dirname "$binary")
                mkdir -p "$symlink_dir"
                ln -sf "$sys_binary" "$binary"
                local ver sha
                ver=$(detect_version "$binary" "$id")
                sha=$(compute_sha256 "$binary")
                registry_set_entry "$id" "$name" "$ver" "$sha" "$size_mb" "$binary" "$tier"
                success "$name linked: $binary → $sys_binary"
            else
                error "$name: system binary $pkg not found"
                return 1
            fi
            ;;

        manual|script)
            warn "$name requires manual installation (source: $source)"
            return 0
            ;;
    esac

    return 0
}

predictive_install() {
    local max_tier="${1:-4}"
    local force="${2:-0}"

    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  JDesk Predictive Native Installer"
    echo "  Galactic Cherry Marvell Edition 98"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""

    # Phase 1: Scan available space
    local avail_mb total_mb used_mb
    avail_mb=$(scan_available_space)
    total_mb=$(scan_total_space)
    used_mb=$(scan_used_by_jdesk)

    echo -e "  ${BOLD}Disk Space Scan:${NC}"
    echo "    Filesystem total: ${total_mb} MB"
    echo "    Available:        ${avail_mb} MB"
    echo "    JDesk current:    ${used_mb} MB"
    echo ""

    # Phase 2: Build install queue ordered by tier then predictive score
    declare -a install_queue=()  # "tier|score|id|name|size_mb|source|binary|pkg"

    local remaining_mb="$avail_mb"
    local planned_mb=0
    local skipped_space=0

    # Process each tier in order
    for tier_num in 1 2 3 4; do
        [ "$tier_num" -gt "$max_tier" ] && break

        local tier_var="TIER${tier_num}_APPS"
        declare -n tier_ref="$tier_var"

        # Compute scores and sort within tier
        declare -a tier_entries=()
        for id in "${!tier_ref[@]}"; do
            IFS='|' read -r name size_mb utility probable source binary pkg <<< "${tier_ref[$id]}"
            local score
            score=$(compute_predictive_score "$utility" "$probable" "$size_mb")
            tier_entries+=("$score|$id|$name|$size_mb|$source|$binary|$pkg")
        done

        # Sort by score descending within tier
        IFS=$'\n' sorted_entries=($(sort -t'|' -k1 -nr <<< "${tier_entries[*]}")); unset IFS

        for entry in "${sorted_entries[@]}"; do
            IFS='|' read -r score id name size_mb source binary pkg <<< "$entry"

            # Space check
            if [ "$size_mb" -gt "$remaining_mb" ]; then
                ((skipped_space++))
                warn "SKIP $name (${size_mb} MB) — insufficient space (${remaining_mb} MB remaining)"
                continue
            fi

            install_queue+=("$tier_num|$score|$id|$name|$size_mb|$source|$binary|$pkg")
            remaining_mb=$((remaining_mb - size_mb))
            planned_mb=$((planned_mb + size_mb))
        done
    done

    # Phase 3: Show plan
    echo -e "  ${BOLD}Install Plan (predictive order):${NC}"
    echo "    ┌─────┬───────┬────────────────────────┬─────────┬────────┐"
    echo "    │ Tier│ Score │ Application            │ Size MB │ Source │"
    echo "    ├─────┼───────┼────────────────────────┼─────────┼────────┤"

    local idx=0
    for entry in "${install_queue[@]}"; do
        IFS='|' read -r tier score id name size_mb source binary pkg <<< "$entry"
        printf "    │  %d  │ %5s │ %-22s │ %7s │ %-6s │\n" "$tier" "$score" "$name" "$size_mb" "$source"
        ((idx++))
    done

    echo "    └─────┴───────┴────────────────────────┴─────────┴────────┘"
    echo ""
    echo "    Planned:  ${planned_mb} MB across ${#install_queue[@]} applications"
    echo "    Skipped:  ${skipped_space} (insufficient space)"
    echo "    Remaining after install: ~${remaining_mb} MB"
    echo ""

    # If --plan, stop here
    if [ "${DRY_RUN:-0}" -eq 1 ]; then
        echo "  (Dry run — no changes made)"
        return 0
    fi

    # Phase 4: Execute installs
    echo -e "  ${BOLD}Installing...${NC}"
    echo ""

    local installed=0 failed=0 skipped=0

    for entry in "${install_queue[@]}"; do
        IFS='|' read -r tier score id name size_mb source binary pkg <<< "$entry"

        echo -e "  [Tier $tier] ${BOLD}$name${NC} (${size_mb} MB, score=$score)"

        # Re-check actual space before each install (another process may have written)
        local actual_avail
        actual_avail=$(scan_available_space)
        if [ "$size_mb" -gt "$actual_avail" ]; then
            warn "Space exhausted during install (need ${size_mb} MB, have ${actual_avail} MB)"
            warn "Stopping. Installed $installed of ${#install_queue[@]} planned."
            break
        fi

        if install_single_native "$id" "$name" "$size_mb" "$source" "$binary" "$pkg" "$tier" "$force"; then
            ((installed++))
        else
            ((failed++))
        fi
        echo ""
    done

    # Phase 5: Summary
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Installation Complete"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo -e "    Installed:  ${GREEN}$installed${NC}"
    echo -e "    Failed:     ${RED}$failed${NC}"
    echo -e "    Skipped:    ${BLUE}$skipped${NC} (already present or no space)"
    echo ""
    echo "    Registry: $REGISTRY_FILE"
    echo "    Check with: predictive-install.sh --check"
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
}

# ---------------------------------------------------------------------------
#  Plan (dry run)
# ---------------------------------------------------------------------------

show_plan() {
    DRY_RUN=1 predictive_install "${1:-4}" 0
}

# ===========================================================================
#  Main
# ===========================================================================

main() {
    # Ensure jq is available
    if ! command -v jq &>/dev/null; then
        echo "Installing jq (required for registry)..."
        apt-get install -y -qq jq 2>/dev/null || { error "jq required but install failed"; exit 1; }
    fi

    init_registry

    case "${1:-}" in
        --check|-c)
            check_all_natives
            ;;
        --delete|-d)
            if [ -z "${2:-}" ]; then
                error "Usage: predictive-install.sh --delete <native-id>"
                echo "  IDs: terminal, files, coreutils, ssh, ide, writer, git, curl,"
                echo "       telnet, browser, wine, darling, gimp, vlc, kali"
                exit 1
            fi
            delete_native "$2"
            ;;
        --plan|-p)
            show_plan "${2:-4}"
            ;;
        --force|-f)
            if [ -z "${2:-}" ]; then
                error "Usage: predictive-install.sh --force <native-id>"
                exit 1
            fi
            # Force reinstall a single native
            local target_id="$2"
            local found=0
            for tier_num in 1 2 3 4; do
                local tier_var="TIER${tier_num}_APPS"
                declare -n tier_ref="$tier_var"
                if [ -n "${tier_ref[$target_id]+x}" ]; then
                    IFS='|' read -r name size_mb utility probable source binary pkg <<< "${tier_ref[$target_id]}"
                    install_single_native "$target_id" "$name" "$size_mb" "$source" "$binary" "$pkg" "$tier_num" 1
                    found=1
                    break
                fi
            done
            [ "$found" -eq 0 ] && error "Unknown native ID: $target_id"
            ;;
        --tier|-t)
            local max_tier="${2:-4}"
            predictive_install "$max_tier" 0
            ;;
        --help|-h)
            echo "Usage: sudo predictive-install.sh [OPTIONS]"
            echo ""
            echo "Predictive Native Installer for JDesk"
            echo "Installs applications in priority order, sized to available disk."
            echo ""
            echo "Options:"
            echo "  (none)              Install all tiers that fit in available space"
            echo "  --check, -c         Check existing natives (version, SHA, installer ID)"
            echo "  --delete, -d <id>   Remove an installed native by ID"
            echo "  --plan, -p [tier]   Show install plan without executing (dry run)"
            echo "  --force, -f <id>    Force reinstall of a specific native"
            echo "  --tier, -t <1-4>    Install only up to specified tier"
            echo "  --help, -h          This message"
            echo ""
            echo "Predictive Tiers:"
            echo "  1 (Essential):   Terminal, Files, Coreutils, SSH"
            echo "  2 (Productive):  IDE, Writer, Git, cURL, Telnet"
            echo "  3 (Connected):   Browser"
            echo "  4 (Extended):    Wine, Darling, GIMP, VLC, Kali"
            echo ""
            echo "Within each tier, apps are sorted by predictive score:"
            echo "  score = (utility × probable_use × 100) / size_mb"
            echo "  Higher score = better value per MB = installed first"
            echo ""
            echo "Registry: $REGISTRY_FILE"
            echo "  Tracks: version, SHA-256, installer ID, install date, install count"
            ;;
        *)
            predictive_install 4 0
            ;;
    esac
}

main "$@"
