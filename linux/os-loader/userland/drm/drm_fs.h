/* SPDX-License-Identifier: GPL-2.0 WITH Linux-syscall-note */
/*
 * drm_fs.h - Shared definitions for DRM (Deferred Remove) subsystem
 *
 * Used by both the kernel module (fs/drm/drm.c) and the userspace
 * tool (tools/drm/drm.c) to ensure consistent constants and interface
 * definitions between kernel and userspace components.
 *
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 */

#ifndef _LINUX_DRM_FS_H
#define _LINUX_DRM_FS_H

/* ============================================================
 * Version
 * ============================================================ */

#define DRM_FS_VERSION_MAJOR	1
#define DRM_FS_VERSION_MINOR	0
#define DRM_FS_VERSION_PATCH	0

/* ============================================================
 * Staging Directory and Paths
 * ============================================================ */

#define DRM_FS_STAGING_DIR	".drm_staging"
#define DRM_FS_SEQ_FILE		".drm_seq"

/* Proc filesystem paths */
#define DRM_FS_PROC_DIR		"drm"
#define DRM_FS_PROC_STATUS	"/proc/drm/status"
#define DRM_FS_PROC_HISTORY	"/proc/drm/history"
#define DRM_FS_PROC_STAGE	"/proc/drm/stage"
#define DRM_FS_PROC_RESTORE	"/proc/drm/restore"
#define DRM_FS_PROC_PENDING	"/proc/drm/pending"

/* ============================================================
 * Limits
 * ============================================================ */

#define DRM_FS_MAX_HISTORY	256	/* Max entries per user */
#define DRM_FS_MAX_USERS	64	/* Max tracked users */
#define DRM_FS_MAX_FILENAME	256	/* Max filename length */

/* ============================================================
 * Default Configuration
 * ============================================================ */

#define DRM_FS_DEFAULT_EXPIRY_HOURS	24	/* Hours before permanent deletion */
#define DRM_FS_DEFAULT_CLEANUP_SEC	3600	/* Cleanup check interval */

/* ============================================================
 * Proc Interface Protocol
 *
 * Communication between userspace tool and kernel module uses
 * simple text-based protocols over /proc/drm/ files.
 * ============================================================ */

/*
 * /proc/drm/stage — Write format (tab-separated):
 *   "<original_path>\t<staged_path>\t<filename>\t<octal_mode>\t<size_bytes>"
 *
 * Example:
 *   "/home/user/doc.txt\t/home/user/.drm_staging/42_doc.txt\tdoc.txt\t644\t1024"
 */
#define DRM_FS_STAGE_SEPARATOR	'\t'
#define DRM_FS_STAGE_FIELDS	5

/*
 * /proc/drm/restore — Write format:
 *   "undo <N>"        Restore last N deletions (most recent first)
 *   "undo-last <N>"   Restore only the Nth most recent deletion
 *   "purge"           Mark all staged files as expired
 */
#define DRM_FS_CMD_UNDO		"undo"
#define DRM_FS_CMD_UNDO_LAST	"undo-last"
#define DRM_FS_CMD_PURGE	"purge"

/*
 * /proc/drm/pending — Read format (tab-separated, one per line):
 *   "<staged_path>\t<original_path>\n"
 *
 * Userspace reads this after issuing a restore command, then
 * performs the rename operations to move files back.
 */
#define DRM_FS_PENDING_SEPARATOR	'\t'

/* ============================================================
 * Entry States
 * ============================================================ */

#define DRM_FS_STATE_ACTIVE	0	/* File staged, can be restored */
#define DRM_FS_STATE_RESTORED	1	/* File restored to original location */
#define DRM_FS_STATE_EXPIRED	2	/* File permanently deleted */

/* ============================================================
 * Kconfig Symbol
 *
 * When building as part of the kernel:
 *   CONFIG_DRM_FS=m   (loadable module)
 *   CONFIG_DRM_FS=y   (built-in, not recommended)
 * ============================================================ */

#define DRM_FS_KCONFIG_SYMBOL	"CONFIG_DRM_FS"

#endif /* _LINUX_DRM_FS_H */
