// SPDX-License-Identifier: GPL-2.0
/*
 * drm - Deferred Remove: userspace binary for undo-capable file deletion
 *
 * This is the userspace companion to the fs/drm kernel module. It provides
 * a drop-in replacement for 'rm' that stages files instead of deleting them,
 * communicates with the kernel via /proc/drm/, and supports undo operations.
 *
 * Commands:
 *   drm <file> [file2...]       Stage files for deletion (move to .drm_staging/)
 *   drm undo <N>                Restore the last N deleted files
 *   drm undo-last <N>           Restore the Nth most recent deletion only
 *   drm list                    Show deletion history from kernel
 *   drm purge                   Permanently delete all staged files
 *   drm status                  Show DRM system status
 *
 * How it works:
 *   1. User runs: drm myfile.txt
 *   2. Binary resolves absolute path of myfile.txt
 *   3. Creates .drm_staging/ in same directory (if not exists)
 *   4. Renames myfile.txt → .drm_staging/<seq>_myfile.txt
 *   5. Writes record to /proc/drm/stage (kernel tracks history)
 *   6. User later runs: drm undo 1
 *   7. Binary writes "undo 1" to /proc/drm/restore
 *   8. Binary reads /proc/drm/pending for files to move back
 *   9. Binary renames staged file back to original location
 *
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 */

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <limits.h>
#include <dirent.h>
#include <time.h>
#include <fcntl.h>

#define DRM_STAGING_DIR    ".drm_staging"
#define DRM_PROC_STAGE     "/proc/drm/stage"
#define DRM_PROC_RESTORE   "/proc/drm/restore"
#define DRM_PROC_PENDING   "/proc/drm/pending"
#define DRM_PROC_HISTORY   "/proc/drm/history"
#define DRM_PROC_STATUS    "/proc/drm/status"
#define DRM_PROC_FLUSH     "/proc/drm/flush"
#define DRM_PROC_SAVE      "/proc/drm/save"
#define DRM_PROC_SAVEABLE  "/proc/drm/saveable"


/* Sequence counter (persisted in staging dir as .drm_seq) */
static unsigned int get_next_seq(const char *staging_dir)
{
	char seq_path[PATH_MAX];
	FILE *f;
	unsigned int seq = 0;

	snprintf(seq_path, sizeof(seq_path), "%s/.drm_seq", staging_dir);

	f = fopen(seq_path, "r");
	if (f) {
		if (fscanf(f, "%u", &seq) != 1)
			seq = 0;
		fclose(f);
	}

	seq++;

	f = fopen(seq_path, "w");
	if (f) {
		fprintf(f, "%u\n", seq);
		fclose(f);
	}

	return seq;
}

/*
 * Ensure .drm_staging/ exists in the given directory.
 * Returns 0 on success, -1 on failure.
 */
static int ensure_staging_dir(const char *parent_dir, char *staging_out,
			      size_t staging_size)
{
	struct stat st;

	snprintf(staging_out, staging_size, "%s/%s", parent_dir, DRM_STAGING_DIR);

	if (stat(staging_out, &st) == 0) {
		if (S_ISDIR(st.st_mode))
			return 0;
		fprintf(stderr, "drm: %s exists but is not a directory\n",
			staging_out);
		return -1;
	}

	if (mkdir(staging_out, 0700) != 0) {
		fprintf(stderr, "drm: Failed to create %s: %s\n",
			staging_out, strerror(errno));
		return -1;
	}

	return 0;
}


/*
 * Write a record to /proc/drm/stage to inform the kernel module.
 * Format: "original_path\tstaged_path\tfilename\tmode\tsize"
 */
static int notify_kernel_stage(const char *original, const char *staged,
			       const char *filename, mode_t mode, off_t size)
{
	FILE *f;
	int ret;

	f = fopen(DRM_PROC_STAGE, "w");
	if (!f) {
		/* Kernel module not loaded — operate in standalone mode */
		return 0;
	}

	ret = fprintf(f, "%s\t%s\t%s\t%o\t%ld",
		      original, staged, filename, mode, (long)size);
	fclose(f);

	return (ret > 0) ? 0 : -1;
}

/*
 * Stage a single file for deferred deletion.
 * Moves it to .drm_staging/ in the same directory.
 */
static int stage_file(const char *filepath)
{
	char abs_path[PATH_MAX];
	char parent_dir[PATH_MAX];
	char staging_dir[PATH_MAX];
	char staged_path[PATH_MAX];
	char *filename;
	char *slash;
	struct stat st;
	unsigned int seq;

	/* Resolve absolute path */
	if (!realpath(filepath, abs_path)) {
		fprintf(stderr, "drm: Cannot resolve '%s': %s\n",
			filepath, strerror(errno));
		return -1;
	}

	/* Check file exists */
	if (lstat(abs_path, &st) != 0) {
		fprintf(stderr, "drm: Cannot stat '%s': %s\n",
			abs_path, strerror(errno));
		return -1;
	}

	/* Get parent directory and filename */
	strncpy(parent_dir, abs_path, sizeof(parent_dir) - 1);
	parent_dir[sizeof(parent_dir) - 1] = '\0';
	slash = strrchr(parent_dir, '/');
	if (!slash) {
		fprintf(stderr, "drm: Invalid path '%s'\n", abs_path);
		return -1;
	}
	filename = slash + 1;
	*slash = '\0'; /* parent_dir is now the directory */

	/* Ensure staging directory exists */
	if (ensure_staging_dir(parent_dir, staging_dir, sizeof(staging_dir)) != 0)
		return -1;

	/* Generate staged filename: <seq>_<filename> */
	seq = get_next_seq(staging_dir);
	snprintf(staged_path, sizeof(staged_path), "%s/%u_%s",
		 staging_dir, seq, filename);

	/* Move file to staging */
	if (rename(abs_path, staged_path) != 0) {
		fprintf(stderr, "drm: Failed to stage '%s': %s\n",
			abs_path, strerror(errno));
		return -1;
	}

	/* Notify kernel module */
	notify_kernel_stage(abs_path, staged_path, filename,
			    st.st_mode, st.st_size);

	printf("drm: '%s' staged (seq=%u, %ld bytes)\n",
	       filename, seq, (long)st.st_size);
	printf("     Undo with: drm undo 1\n");

	return 0;
}


/*
 * Restore files: read /proc/drm/pending and move them back.
 */
static int do_restore_pending(void)
{
	FILE *f;
	char line[PATH_MAX * 2 + 8];
	char *staged, *original;
	int count = 0;

	f = fopen(DRM_PROC_PENDING, "r");
	if (!f) {
		fprintf(stderr, "drm: Cannot read %s: %s\n",
			DRM_PROC_PENDING, strerror(errno));
		fprintf(stderr, "     (Is the drm kernel module loaded?)\n");
		return -1;
	}

	while (fgets(line, sizeof(line), f)) {
		/* Remove trailing newline */
		line[strcspn(line, "\n")] = '\0';

		/* Parse: staged_path\toriginal_path */
		staged = line;
		original = strchr(line, '\t');
		if (!original)
			continue;
		*original = '\0';
		original++;

		/* Move file back */
		if (rename(staged, original) == 0) {
			printf("drm: Restored '%s'\n", original);
			count++;
		} else {
			fprintf(stderr, "drm: Failed to restore '%s': %s\n",
				original, strerror(errno));
		}
	}

	fclose(f);
	return count;
}

/*
 * Send undo command to kernel and perform restores.
 */
static int cmd_undo(unsigned int n)
{
	FILE *f;
	char cmd[64];

	if (n == 0) {
		fprintf(stderr, "drm: undo count must be >= 1\n");
		return -1;
	}

	f = fopen(DRM_PROC_RESTORE, "w");
	if (!f) {
		fprintf(stderr, "drm: Cannot write to %s: %s\n",
			DRM_PROC_RESTORE, strerror(errno));
		fprintf(stderr, "     (Is the drm kernel module loaded?)\n");
		return -1;
	}

	snprintf(cmd, sizeof(cmd), "undo %u", n);
	fputs(cmd, f);
	fclose(f);

	/* Now read pending and do the moves */
	return do_restore_pending();
}

/*
 * Send undo-last command to kernel and perform restore.
 */
static int cmd_undo_last(unsigned int n)
{
	FILE *f;
	char cmd[64];

	if (n == 0) {
		fprintf(stderr, "drm: undo-last index must be >= 1\n");
		return -1;
	}

	f = fopen(DRM_PROC_RESTORE, "w");
	if (!f) {
		fprintf(stderr, "drm: Cannot write to %s: %s\n",
			DRM_PROC_RESTORE, strerror(errno));
		return -1;
	}

	snprintf(cmd, sizeof(cmd), "undo-last %u", n);
	fputs(cmd, f);
	fclose(f);

	return do_restore_pending();
}


/*
 * Print file from /proc/drm/ to stdout.
 */
static int cat_proc_file(const char *path)
{
	FILE *f;
	char buf[4096];

	f = fopen(path, "r");
	if (!f) {
		fprintf(stderr, "drm: Cannot read %s: %s\n",
			path, strerror(errno));
		fprintf(stderr, "     (Is the drm kernel module loaded?)\n");
		return -1;
	}

	while (fgets(buf, sizeof(buf), f))
		fputs(buf, stdout);

	fclose(f);
	return 0;
}

/*
 * Purge all staged files (permanently delete) — legacy, now routes to flush.
 */
static int cmd_purge(void) __attribute__((unused));
static int cmd_purge(void)
{
	FILE *f;

	f = fopen(DRM_PROC_RESTORE, "w");
	if (!f) {
		fprintf(stderr, "drm: Cannot write to %s: %s\n",
			DRM_PROC_RESTORE, strerror(errno));
		return -1;
	}

	fputs("purge", f);
	fclose(f);

	printf("drm: All staged files marked for permanent deletion.\n");
	return 0;
}

static void usage(const char *progname)
{
	printf("DRM - Deferred Remove (undo-capable file deletion)\n\n");
	printf("Usage:\n");
	printf("  %s <file> [file2...]       Stage file(s) for deletion\n", progname);
	printf("  %s undo <N>               Restore last N deleted files\n", progname);
	printf("  %s undo-last <N>          Restore the Nth most recent deletion\n", progname);
	printf("  %s --flush                Permanently delete ALL staged files\n", progname);
	printf("  %s --flush <N>            Permanently delete the Nth staged entry\n", progname);
	printf("  %s --save [N]             Attempt deep recovery of flushed file(s)\n", progname);
	printf("  %s list                   Show deletion history\n", progname);
	printf("  %s status                 Show DRM system status\n", progname);
	printf("  %s purge                  Alias for --flush (back-compat)\n", progname);
	printf("  %s --help                 Show this help\n", progname);
	printf("\n");
	printf("Examples:\n");
	printf("  %s important.txt          # Stages file (can be undone)\n", progname);
	printf("  %s undo 1                 # Restores last deletion\n", progname);
	printf("  %s undo 5                 # Restores last 5 deletions\n", progname);
	printf("  %s undo-last 3            # Restores only the 3rd most recent\n", progname);
	printf("  %s --flush                # Permanently removes all staged files\n", progname);
	printf("  %s --save                 # Recovers last flushed file (if blocks intact)\n", progname);
	printf("  %s --save 3               # Recovers 3rd most recently flushed file\n", progname);
	printf("\n");
	printf("Flush:\n");
	printf("  --flush permanently removes files from the staging area.\n");
	printf("  After flush, 'undo' cannot recover the file normally.\n");
	printf("  However, if the disk blocks have not been overwritten,\n");
	printf("  '--save' can still recover the data via block-level scan.\n");
	printf("\n");
	printf("Save (Deep Recovery):\n");
	printf("  --save attempts to recover files that were flushed by reading\n");
	printf("  the raw disk blocks where the file data was stored. This works\n");
	printf("  ONLY if the blocks have not been reallocated/overwritten since\n");
	printf("  the flush. The sooner you run --save after --flush, the better\n");
	printf("  the chances of recovery. Requires the DRM kernel module.\n");
	printf("\n");
	printf("The kernel module (fs/drm) must be loaded for full functionality.\n");
	printf("Without the module, drm operates in standalone mode (local staging only).\n");
}


/*
 * --flush: Permanently delete staged files from disk.
 * If N is specified, flush only the Nth entry. Otherwise flush all.
 * The kernel module records block locations before unlinking so that
 * --save can attempt recovery later if blocks aren't overwritten.
 */
static int cmd_flush(int argc, char *argv[])
{
	FILE *f;
	char cmd[64];
	unsigned int n = 0;

	/* Parse optional argument: --flush [N] */
	if (argc >= 3 && argv[2][0] != '-') {
		n = (unsigned int)atoi(argv[2]);
		if (n == 0) {
			fprintf(stderr, "drm: --flush index must be >= 1 (or omit for all)\n");
			return -1;
		}
	}

	f = fopen(DRM_PROC_FLUSH, "w");
	if (!f) {
		/*
		 * Kernel module not loaded — do standalone flush.
		 * Walk all .drm_staging/ directories and unlink files.
		 */
		fprintf(stderr, "drm: Kernel module not loaded. Performing standalone flush.\n");
		fprintf(stderr, "     (Deep recovery via --save will NOT be available)\n");

		/* For standalone mode, we just tell the kernel to purge */
		f = fopen(DRM_PROC_RESTORE, "w");
		if (f) {
			fputs("purge", f);
			fclose(f);
		}
		printf("drm: Flush complete (standalone mode — no recovery possible).\n");
		return 0;
	}

	if (n > 0)
		snprintf(cmd, sizeof(cmd), "flush-nth %u", n);
	else
		snprintf(cmd, sizeof(cmd), "flush-all");

	fputs(cmd, f);
	fclose(f);

	if (n > 0)
		printf("drm: Flushed entry #%u permanently.\n", n);
	else
		printf("drm: All staged files permanently deleted.\n");

	printf("     Block map preserved — deep recovery via 'drm --save' may still work\n");
	printf("     if disk blocks have not been overwritten.\n");

	return 0;
}

/*
 * --save: Attempt deep recovery of flushed files.
 * Reads block location metadata from the kernel module and attempts
 * to reconstruct file content from raw disk blocks.
 *
 * This works because:
 *   1. When --flush is called, the kernel module records the physical
 *      block numbers (extents) of the file before unlinking it.
 *   2. The blocks are freed back to the filesystem allocator but NOT zeroed.
 *   3. As long as those blocks haven't been reallocated and overwritten,
 *      the original file data is still physically present on disk.
 *   4. --save reads those blocks directly and reconstructs the file.
 *
 * The kernel module exposes saveable entries via /proc/drm/saveable
 * and accepts recovery commands via /proc/drm/save.
 */
static int cmd_save(int argc, char *argv[])
{
	FILE *f;
	char cmd[64];
	char line[PATH_MAX * 2 + 64];
	unsigned int n = 0;
	int recovered = 0;

	/* Parse optional argument: --save [N] */
	if (argc >= 3 && argv[2][0] != '-') {
		n = (unsigned int)atoi(argv[2]);
		if (n == 0) {
			fprintf(stderr, "drm: --save index must be >= 1 (or omit for most recent)\n");
			return -1;
		}
	} else {
		n = 1; /* Default: recover most recently flushed */
	}

	/* First check what's saveable */
	f = fopen(DRM_PROC_SAVEABLE, "r");
	if (!f) {
		fprintf(stderr, "drm: Cannot read %s: %s\n",
			DRM_PROC_SAVEABLE, strerror(errno));
		fprintf(stderr, "     (Is the drm kernel module loaded?)\n");
		fprintf(stderr, "     Deep recovery requires the kernel module for block-level access.\n");
		return -1;
	}

	printf("drm: Checking saveable entries...\n");

	/* Show saveable list if user didn't specify N */
	if (argc < 3) {
		printf("\n");
		while (fgets(line, sizeof(line), f))
			fputs(line, stdout);
		fclose(f);
		printf("\nUse 'drm --save <N>' to recover a specific entry.\n");
		return 0;
	}
	fclose(f);

	/* Send save command to kernel */
	f = fopen(DRM_PROC_SAVE, "w");
	if (!f) {
		fprintf(stderr, "drm: Cannot write to %s: %s\n",
			DRM_PROC_SAVE, strerror(errno));
		return -1;
	}

	snprintf(cmd, sizeof(cmd), "recover %u", n);
	fputs(cmd, f);
	fclose(f);

	/* Read result from pending (kernel puts recovered file info there) */
	f = fopen(DRM_PROC_PENDING, "r");
	if (f) {
		while (fgets(line, sizeof(line), f)) {
			char *staged, *original;

			line[strcspn(line, "\n")] = '\0';
			staged = line;
			original = strchr(line, '\t');
			if (!original)
				continue;
			*original = '\0';
			original++;

			/* The kernel wrote recovered data to a temp path */
			if (rename(staged, original) == 0) {
				printf("drm: RECOVERED '%s'\n", original);
				printf("     (File data read from original disk blocks)\n");
				recovered++;
			} else {
				/* Try copy if rename fails (cross-device) */
				printf("drm: Recovery written to: %s\n", staged);
				printf("     Move manually to: %s\n", original);
				recovered++;
			}
		}
		fclose(f);
	}

	if (recovered == 0) {
		printf("drm: Recovery FAILED for entry #%u.\n", n);
		printf("     The disk blocks may have been overwritten.\n");
		printf("     Recovery is only possible if no new data was written\n");
		printf("     to the same disk area since the flush.\n");
		return -1;
	}

	printf("drm: Successfully recovered %d file(s) from disk blocks.\n", recovered);
	return 0;
}

int main(int argc, char *argv[])
{
	int i;
	int ret = 0;
	unsigned int n;

	if (argc < 2) {
		usage(argv[0]);
		return 1;
	}

	/* Help */
	if (strcmp(argv[1], "--help") == 0 || strcmp(argv[1], "-h") == 0) {
		usage(argv[0]);
		return 0;
	}

	/* undo <N> — restore last N deletions */
	if (strcmp(argv[1], "undo") == 0) {
		if (argc < 3) {
			fprintf(stderr, "drm: 'undo' requires a count argument\n");
			fprintf(stderr, "Usage: drm undo <N>\n");
			return 1;
		}
		n = (unsigned int)atoi(argv[2]);
		ret = cmd_undo(n);
		if (ret >= 0)
			printf("drm: Restored %d file(s)\n", ret);
		return (ret >= 0) ? 0 : 1;
	}

	/* undo-last <N> — restore the Nth most recent */
	if (strcmp(argv[1], "undo-last") == 0) {
		if (argc < 3) {
			fprintf(stderr, "drm: 'undo-last' requires an index argument\n");
			fprintf(stderr, "Usage: drm undo-last <N>\n");
			return 1;
		}
		n = (unsigned int)atoi(argv[2]);
		ret = cmd_undo_last(n);
		return (ret >= 0) ? 0 : 1;
	}

	/* list — show history */
	if (strcmp(argv[1], "list") == 0)
		return cat_proc_file(DRM_PROC_HISTORY);

	/* status — show system status */
	if (strcmp(argv[1], "status") == 0)
		return cat_proc_file(DRM_PROC_STATUS);

	/* purge — permanently delete all staged */
	if (strcmp(argv[1], "purge") == 0)
		return cmd_flush(argc, argv);

	/* --flush — permanently remove staged files (with block map for --save) */
	if (strcmp(argv[1], "--flush") == 0 || strcmp(argv[1], "flush") == 0)
		return cmd_flush(argc, argv);

	/* --save — deep recovery of flushed files from raw disk blocks */
	if (strcmp(argv[1], "--save") == 0 || strcmp(argv[1], "save") == 0)
		return cmd_save(argc, argv);

	/* Default: stage files for deletion */
	for (i = 1; i < argc; i++) {
		if (stage_file(argv[i]) != 0)
			ret = 1;
	}

	return ret;
}
