#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
#
# jdesk-vfs-setup.sh — JDesk Virtual File System Provisioner
#
# Creates and mounts two virtual disk images for JDesk internal storage:
#   1. EXT4 image  — Primary JDesk file storage (Linux native, journaled)
#   2. NTFS image  — Cross-platform exchange partition (Windows-compatible)
#
# Both images are loop-mounted at boot and accessible from the JDesk Files
# application via dedicated sidebar entries.
#
# Layout:
#   /opt/jdesk/vfs/jdesk-ext4.img   → mounted at /opt/jdesk/home/  (EXT4)
#   /opt/jdesk/vfs/jdesk-ntfs.img   → mounted at /opt/jdesk/share/ (NTFS)
#
# JDesk Files sidebar:
#   📂 JDesk Home     → /opt/jdesk/home/    (EXT4 — fast, journaled, Linux)
#   💾 JDesk Share    → /opt/jdesk/share/   (NTFS — cross-platform exchange)
#   💻 Native System  → /                    (host OS filesystem)
#
# Why both EXT4 and NTFS?
#   - EXT4 is optimal for Linux-native JDesk operations (inodes, permissions,
#     xattrs, hardlinks, sparse files, journaling, fast fsync)
#   - NTFS enables seamless file exchange with Windows hosts, USB drives,
#     and dual-boot systems. Files placed in /opt/jdesk/share/ are readable
#     on Windows without conversion.
#
# Disk Budget:
#   EXT4 image: 2 GB (expandable to 8 GB)
#   NTFS image: 1 GB (expandable to 4 GB)
#   Total VFS:  3 GB initial
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

set -euo pipefail

# ============================================================================
#  Configuration
# ============================================================================

VFS_DIR="/opt/jdesk/vfs"
EXT4_IMG="$VFS_DIR/jdesk-ext4.img"
NTFS_IMG="$VFS_DIR/jdesk-ntfs.img"
EXT4_MOUNT="/opt/jdesk/home"
NTFS_MOUNT="/opt/jdesk/share"

EXT4_SIZE_MB=2048    # 2 GB initial
NTFS_SIZE_MB=1024    # 1 GB initial

EXT4_LABEL="JDesk-Home"
NTFS_LABEL="JDesk-Share"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  JDesk Virtual File System Provisioner${NC}"
echo -e "${GREEN}  Galactic Cherry Marvell Edition 98${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""

# ============================================================================
#  Prerequisites
# ============================================================================

echo -e "${GREEN}[1/7]${NC} Checking prerequisites..."

# Need root for loop mount
if [ "$(id -u)" -ne 0 ]; then
    echo -e "${RED}[ERROR]${NC} Must run as root (for loop device mount)."
    echo "  Usage: sudo $0"
    exit 1
fi

# Check for mkfs utilities
for tool in mkfs.ext4 losetup mount; do
    if ! command -v "$tool" &>/dev/null; then
        echo -e "${RED}[ERROR]${NC} Required tool not found: $tool"
        exit 1
    fi
done

# NTFS support (ntfs-3g or mkntfs)
HAVE_NTFS=0
if command -v mkntfs &>/dev/null || command -v mkfs.ntfs &>/dev/null; then
    HAVE_NTFS=1
else
    echo -e "${YELLOW}[WARN]${NC} ntfs-3g not found. Installing..."
    apt-get install -y ntfs-3g 2>/dev/null || {
        echo -e "${YELLOW}[WARN]${NC} Cannot install ntfs-3g. NTFS image will be skipped."
        echo "  Install manually: apt install ntfs-3g"
    }
    command -v mkntfs &>/dev/null && HAVE_NTFS=1
fi

echo "  ✓ Prerequisites satisfied"
echo ""

# ============================================================================
#  Create Directories
# ============================================================================

echo -e "${GREEN}[2/7]${NC} Creating mount points..."

mkdir -p "$VFS_DIR"
mkdir -p "$EXT4_MOUNT"
mkdir -p "$NTFS_MOUNT"

echo "  ✓ $VFS_DIR"
echo "  ✓ $EXT4_MOUNT"
echo "  ✓ $NTFS_MOUNT"
echo ""

# ============================================================================
#  Create EXT4 Image
# ============================================================================

echo -e "${GREEN}[3/7]${NC} Creating EXT4 image ($EXT4_SIZE_MB MB)..."

if [ -f "$EXT4_IMG" ]; then
    echo -e "${YELLOW}[SKIP]${NC} EXT4 image already exists: $EXT4_IMG"
    echo "  Size: $(du -h "$EXT4_IMG" | cut -f1)"
else
    # Create sparse file (doesn't consume full space immediately)
    truncate -s ${EXT4_SIZE_MB}M "$EXT4_IMG"

    # Format as EXT4 with JDesk-optimized parameters
    mkfs.ext4 -q \
        -L "$EXT4_LABEL" \
        -O has_journal,extent,flex_bg,huge_file,dir_nlink,extra_isize,64bit \
        -E lazy_itable_init=1,lazy_journal_init=1 \
        -I 256 \
        -i 16384 \
        -m 1 \
        "$EXT4_IMG"

    echo "  ✓ Created: $EXT4_IMG (${EXT4_SIZE_MB} MB, sparse)"
    echo "  Label:    $EXT4_LABEL"
    echo "  Features: journal, extents, flex_bg, 64bit"
    echo "  Reserved: 1% (for root)"
fi
echo ""

# ============================================================================
#  Create NTFS Image
# ============================================================================

echo -e "${GREEN}[4/7]${NC} Creating NTFS image ($NTFS_SIZE_MB MB)..."

if [ -f "$NTFS_IMG" ]; then
    echo -e "${YELLOW}[SKIP]${NC} NTFS image already exists: $NTFS_IMG"
    echo "  Size: $(du -h "$NTFS_IMG" | cut -f1)"
elif [ "$HAVE_NTFS" -eq 1 ]; then
    # Create sparse file
    truncate -s ${NTFS_SIZE_MB}M "$NTFS_IMG"

    # Format as NTFS
    MKNTFS_CMD=$(command -v mkntfs 2>/dev/null || command -v mkfs.ntfs 2>/dev/null)
    "$MKNTFS_CMD" -Q -L "$NTFS_LABEL" "$NTFS_IMG" >/dev/null 2>&1

    echo "  ✓ Created: $NTFS_IMG (${NTFS_SIZE_MB} MB, sparse)"
    echo "  Label:    $NTFS_LABEL"
    echo "  Features: NTFS 3.1, MFT, journal, compression-ready"
else
    echo -e "${YELLOW}[SKIP]${NC} NTFS tools not available. Skipping NTFS image."
fi
echo ""

# ============================================================================
#  Mount Images
# ============================================================================

echo -e "${GREEN}[5/7]${NC} Mounting virtual filesystems..."

# Mount EXT4
if mountpoint -q "$EXT4_MOUNT" 2>/dev/null; then
    echo -e "${YELLOW}[ALREADY]${NC} EXT4 already mounted at $EXT4_MOUNT"
else
    mount -o loop,rw,noatime,nodiratime "$EXT4_IMG" "$EXT4_MOUNT"
    echo "  ✓ EXT4 mounted: $EXT4_MOUNT"
fi

# Mount NTFS
if [ -f "$NTFS_IMG" ]; then
    if mountpoint -q "$NTFS_MOUNT" 2>/dev/null; then
        echo -e "${YELLOW}[ALREADY]${NC} NTFS already mounted at $NTFS_MOUNT"
    else
        # Use ntfs-3g for full read-write NTFS support
        if command -v ntfs-3g &>/dev/null; then
            ntfs-3g -o loop,rw,big_writes,noatime "$NTFS_IMG" "$NTFS_MOUNT"
        else
            mount -o loop,rw "$NTFS_IMG" "$NTFS_MOUNT"
        fi
        echo "  ✓ NTFS mounted: $NTFS_MOUNT (ntfs-3g, read-write)"
    fi
fi
echo ""

# ============================================================================
#  Populate Default JDesk Directory Structure
# ============================================================================

echo -e "${GREEN}[6/7]${NC} Creating JDesk directory structure..."

# EXT4 home — full JDesk structure
for dir in Desktop Documents Downloads Music Pictures Videos \
           Projects Templates Public .config .local/share; do
    mkdir -p "$EXT4_MOUNT/$dir"
done

# JDesk-specific directories
mkdir -p "$EXT4_MOUNT/.jdesk/settings"
mkdir -p "$EXT4_MOUNT/.jdesk/cache"
mkdir -p "$EXT4_MOUNT/.jdesk/themes"
mkdir -p "$EXT4_MOUNT/.jdesk/plugins"
mkdir -p "$EXT4_MOUNT/Projects/java"
mkdir -p "$EXT4_MOUNT/Projects/web"
mkdir -p "$EXT4_MOUNT/Projects/scripts"

# Ownership (to the primary user, UID 1000)
chown -R 1000:1000 "$EXT4_MOUNT" 2>/dev/null || true

echo "  ✓ Created: Desktop, Documents, Downloads, Music, Pictures, Videos"
echo "  ✓ Created: Projects/{java,web,scripts}"
echo "  ✓ Created: .jdesk/{settings,cache,themes,plugins}"

# NTFS share — exchange directories
if [ -f "$NTFS_IMG" ] && mountpoint -q "$NTFS_MOUNT" 2>/dev/null; then
    mkdir -p "$NTFS_MOUNT/Exchange"
    mkdir -p "$NTFS_MOUNT/Transfer"
    mkdir -p "$NTFS_MOUNT/Shared Documents"
    mkdir -p "$NTFS_MOUNT/USB Import"
    echo "  ✓ NTFS: Exchange, Transfer, Shared Documents, USB Import"
fi
echo ""

# ============================================================================
#  Generate fstab Entries
# ============================================================================

echo -e "${GREEN}[7/7]${NC} Generating fstab entries..."

FSTAB_EXT4="$EXT4_IMG  $EXT4_MOUNT  ext4  loop,rw,noatime,nodiratime,nofail  0  2"
FSTAB_NTFS="$NTFS_IMG  $NTFS_MOUNT  ntfs-3g  loop,rw,big_writes,noatime,nofail  0  0"

# Write JDesk fstab fragment (don't modify system fstab directly)
FSTAB_FRAGMENT="/opt/jdesk/vfs/jdesk-vfs.fstab"
cat > "$FSTAB_FRAGMENT" <<EOF
# JDesk Virtual File System — mount entries
# Source: jdesk-vfs-setup.sh
# Add these to /etc/fstab for persistent mount, or use systemd .mount units
#
# EXT4 — JDesk Home (primary internal storage, journaled)
$FSTAB_EXT4
#
# NTFS — JDesk Share (cross-platform exchange, Windows-compatible)
$FSTAB_NTFS
EOF

echo "  ✓ Written: $FSTAB_FRAGMENT"
echo ""
echo "  To auto-mount at boot, append to /etc/fstab:"
echo "    cat $FSTAB_FRAGMENT >> /etc/fstab"
echo ""
echo "  Or create systemd mount units (preferred)."
echo ""

# ============================================================================
#  Summary
# ============================================================================

echo "═══════════════════════════════════════════════════════════════════"
echo "  JDesk VFS — Setup Complete"
echo "═══════════════════════════════════════════════════════════════════"
echo ""
echo "  Virtual Filesystems:"
echo "    ┌────────────────────────────────────────────────────────────┐"
echo "    │ Filesystem │ Mount Point       │ Size   │ Type │ Purpose  │"
echo "    ├────────────────────────────────────────────────────────────┤"
echo "    │ EXT4       │ /opt/jdesk/home/  │ 2 GB   │ ext4 │ Internal │"
echo "    │ NTFS       │ /opt/jdesk/share/ │ 1 GB   │ ntfs │ Exchange │"
echo "    └────────────────────────────────────────────────────────────┘"
echo ""
echo "  JDesk Files Sidebar:"
echo "    📂 JDesk Home    → /opt/jdesk/home/   (EXT4, journaled)"
echo "    💾 JDesk Share   → /opt/jdesk/share/  (NTFS, cross-platform)"
echo "    💻 Native System → /                   (host OS)"
echo ""
echo "  Expand later:"
echo "    truncate -s 8G $EXT4_IMG && resize2fs $EXT4_IMG"
echo "    truncate -s 4G $NTFS_IMG && ntfsresize $NTFS_IMG"
echo ""
echo "═══════════════════════════════════════════════════════════════════"
