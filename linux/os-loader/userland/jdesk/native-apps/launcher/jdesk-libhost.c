/* SPDX-License-Identifier: GPL-2.0 */
/*
 * jdesk-libhost.c — JDesk Library Host Process
 *
 * Sandboxed host process for executing shared libraries (.so) as runnables.
 * Loads a library via dlopen(), resolves its entry point via dlsym(),
 * and invokes it with the provided arguments.
 *
 * This process runs under java -memory-guard, providing resource governance.
 * It communicates status back to JDesk via a Unix domain socket.
 *
 * Usage:
 *   jdesk-libhost --library /path/to.so --entry main --role daemon \
 *                 --lib-path /opt/jdesk/libs:/usr/lib \
 *                 [--cross-link kernel32.dll=libc.so.6] \
 *                 -- [args to entry point]
 *
 * Open-source dependencies:
 *   - dlopen/dlsym/dlerror (glibc, LGPL-2.1) — standard dynamic linker
 *   - libelf (elfutils, GPL-2.0/LGPL-3.0) — optional, for deep inspection
 *
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <dlfcn.h>
#include <signal.h>
#include <unistd.h>
#include <getopt.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <errno.h>
#include <time.h>

/* ===========================================================================
 * Constants
 * ===========================================================================
 */

#define JDESK_LIBHOST_VERSION   "1.0.0"
#define MAX_LIB_PATH            4096
#define MAX_ENTRY_NAME          256
#define MAX_CROSS_LINKS         64
#define MAX_LIB_SEARCH_PATHS    32
#define IPC_SOCKET_PATH_FMT     "/var/run/jdesk/libhost-%d.sock"

/* ===========================================================================
 * Entry Point Function Signatures
 * ===========================================================================
 */

/* Standard C main() signature */
typedef int (*entry_main_t)(int argc, char **argv);

/* JDesk plugin init — returns 0 on success */
typedef int (*entry_jdesk_start_t)(int argc, char **argv, void *jdesk_context);

/* Service main — long-running, blocks until shutdown */
typedef int (*entry_service_main_t)(int argc, char **argv);

/* Module init — no arguments, returns 0 on success */
typedef int (*entry_module_init_t)(void);

/* Generic void init */
typedef void (*entry_void_init_t)(void);

/* ===========================================================================
 * Cross-Link Entry
 * ===========================================================================
 */

struct cross_link {
	char original[256];   /* Original dependency name (e.g. "kernel32.dll") */
	char replacement[256]; /* Replacement library (e.g. "libc.so.6") */
};

/* ===========================================================================
 * Host State
 * ===========================================================================
 */

struct libhost_state {
	char library_path[MAX_LIB_PATH];
	char entry_name[MAX_ENTRY_NAME];
	char role[64];                    /* daemon, service, desktop_app, plugin, library */
	char lib_paths[MAX_LIB_SEARCH_PATHS][MAX_LIB_PATH];
	int  lib_path_count;
	struct cross_link cross_links[MAX_CROSS_LINKS];
	int  cross_link_count;
	void *lib_handle;                 /* dlopen() handle */
	void *entry_func;                 /* Resolved entry point */
	int  ipc_fd;                      /* Unix socket for JDesk communication */
	bool running;
	pid_t pid;
	time_t start_time;
};

static struct libhost_state state;

/* ===========================================================================
 * Signal Handling
 * ===========================================================================
 */

static void signal_handler(int sig)
{
	fprintf(stderr, "[jdesk-libhost] Received signal %d (%s)\n", sig, strsignal(sig));

	switch (sig) {
	case SIGTERM:
	case SIGINT:
		state.running = false;
		fprintf(stderr, "[jdesk-libhost] Shutting down gracefully...\n");
		break;
	case SIGSEGV:
	case SIGBUS:
	case SIGABRT:
		fprintf(stderr, "[jdesk-libhost] FATAL: Library crashed (%s)\n", strsignal(sig));
		fprintf(stderr, "[jdesk-libhost] Library: %s\n", state.library_path);
		fprintf(stderr, "[jdesk-libhost] Entry:   %s\n", state.entry_name);
		/* Clean up and exit */
		if (state.lib_handle) dlclose(state.lib_handle);
		_exit(128 + sig);
		break;
	}
}

static void install_signal_handlers(void)
{
	struct sigaction sa;
	memset(&sa, 0, sizeof(sa));
	sa.sa_handler = signal_handler;
	sigemptyset(&sa.sa_mask);

	sigaction(SIGTERM, &sa, NULL);
	sigaction(SIGINT, &sa, NULL);
	sigaction(SIGSEGV, &sa, NULL);
	sigaction(SIGBUS, &sa, NULL);
	sigaction(SIGABRT, &sa, NULL);
}

/* ===========================================================================
 * Library Path Configuration
 * ===========================================================================
 */

/**
 * Set LD_LIBRARY_PATH to include all configured search paths.
 * This allows the loaded library's dependencies to be found.
 */
static void configure_library_paths(void)
{
	if (state.lib_path_count == 0) return;

	/* Build combined path string */
	char combined[MAX_LIB_PATH * MAX_LIB_SEARCH_PATHS];
	combined[0] = '\0';

	for (int i = 0; i < state.lib_path_count; i++) {
		if (i > 0) strcat(combined, ":");
		strncat(combined, state.lib_paths[i], MAX_LIB_PATH - 1);
	}

	/* Append existing LD_LIBRARY_PATH */
	const char *existing = getenv("LD_LIBRARY_PATH");
	if (existing && existing[0]) {
		strcat(combined, ":");
		strncat(combined, existing, 1024);
	}

	setenv("LD_LIBRARY_PATH", combined, 1);

	fprintf(stderr, "[jdesk-libhost] Library search paths: %s\n", combined);
}

/* ===========================================================================
 * Cross-Link Preloading
 * ===========================================================================
 */

/**
 * Pre-load cross-linked libraries before the main library.
 * This satisfies dependencies that map to different names on this platform.
 *
 * E.g., a Windows DLL needs "kernel32.dll" → we preload "libc.so.6"
 * under a symbol alias.
 */
static void preload_cross_links(void)
{
	for (int i = 0; i < state.cross_link_count; i++) {
		struct cross_link *cl = &state.cross_links[i];
		fprintf(stderr, "[jdesk-libhost] Cross-link: %s → %s\n",
			cl->original, cl->replacement);

		/* Attempt to preload the replacement library */
		void *handle = dlopen(cl->replacement, RTLD_NOW | RTLD_GLOBAL);
		if (!handle) {
			fprintf(stderr, "[jdesk-libhost] Warning: cannot preload %s: %s\n",
				cl->replacement, dlerror());
		}
		/* Handle intentionally leaked (RTLD_GLOBAL makes symbols available) */
	}
}

/* ===========================================================================
 * Library Loading
 * ===========================================================================
 */

static int load_library(void)
{
	fprintf(stderr, "[jdesk-libhost] Loading: %s\n", state.library_path);

	/* Verify library file exists and is readable */
	struct stat st;
	if (stat(state.library_path, &st) != 0) {
		fprintf(stderr, "[jdesk-libhost] ERROR: Cannot stat library: %s\n",
			strerror(errno));
		return -1;
	}

	if (!S_ISREG(st.st_mode)) {
		fprintf(stderr, "[jdesk-libhost] ERROR: Not a regular file: %s\n",
			state.library_path);
		return -1;
	}

	/* Configure search paths for dependencies */
	configure_library_paths();

	/* Pre-load cross-linked libraries */
	preload_cross_links();

	/* Open the library
	 * RTLD_NOW: resolve all symbols immediately (fail fast)
	 * RTLD_LOCAL: keep symbols private to this library (isolation)
	 */
	state.lib_handle = dlopen(state.library_path, RTLD_NOW | RTLD_LOCAL);
	if (!state.lib_handle) {
		fprintf(stderr, "[jdesk-libhost] ERROR: dlopen failed: %s\n", dlerror());
		return -1;
	}

	fprintf(stderr, "[jdesk-libhost] ✓ Library loaded successfully\n");

	/* Resolve entry point */
	if (state.entry_name[0]) {
		dlerror(); /* Clear any prior error */
		state.entry_func = dlsym(state.lib_handle, state.entry_name);
		const char *err = dlerror();
		if (err) {
			fprintf(stderr, "[jdesk-libhost] ERROR: Cannot resolve '%s': %s\n",
				state.entry_name, err);
			dlclose(state.lib_handle);
			state.lib_handle = NULL;
			return -1;
		}
		fprintf(stderr, "[jdesk-libhost] ✓ Entry point resolved: %s @ %p\n",
			state.entry_name, state.entry_func);
	} else {
		fprintf(stderr, "[jdesk-libhost] No entry point specified — library loaded but not invoked\n");
	}

	return 0;
}

/* ===========================================================================
 * Entry Point Invocation
 * ===========================================================================
 */

static int invoke_entry(int argc, char **argv)
{
	if (!state.entry_func) {
		fprintf(stderr, "[jdesk-libhost] No entry point to invoke\n");
		return 0;
	}

	fprintf(stderr, "[jdesk-libhost] Invoking: %s (role=%s)\n",
		state.entry_name, state.role);
	fprintf(stderr, "[jdesk-libhost] ──────────────────────────────────────\n");

	state.running = true;
	int result = 0;

	/* Determine calling convention based on entry point name and role */
	if (strcmp(state.entry_name, "jdesk_start") == 0) {
		/* JDesk plugin: int jdesk_start(int argc, char **argv, void *ctx) */
		entry_jdesk_start_t func = (entry_jdesk_start_t)state.entry_func;
		result = func(argc, argv, NULL /* TODO: JDesk context via IPC */);

	} else if (strcmp(state.entry_name, "module_init") == 0 ||
		   strcmp(state.entry_name, "plugin_init") == 0 ||
		   strcmp(state.entry_name, "_init") == 0) {
		/* No-arg init: int init(void) */
		entry_module_init_t func = (entry_module_init_t)state.entry_func;
		result = func();

	} else {
		/* Default: treat as int main(int argc, char **argv) */
		entry_main_t func = (entry_main_t)state.entry_func;
		result = func(argc, argv);
	}

	fprintf(stderr, "[jdesk-libhost] ──────────────────────────────────────\n");
	fprintf(stderr, "[jdesk-libhost] Entry point returned: %d\n", result);

	return result;
}

/* ===========================================================================
 * IPC Setup (optional — for JDesk desktop communication)
 * ===========================================================================
 */

static int setup_ipc(void)
{
	char sock_path[256];
	snprintf(sock_path, sizeof(sock_path), IPC_SOCKET_PATH_FMT, getpid());

	/* Ensure directory exists */
	mkdir("/var/run/jdesk", 0755);

	state.ipc_fd = socket(AF_UNIX, SOCK_STREAM, 0);
	if (state.ipc_fd < 0) {
		/* Non-fatal — IPC is optional */
		fprintf(stderr, "[jdesk-libhost] IPC unavailable: %s\n", strerror(errno));
		return -1;
	}

	struct sockaddr_un addr;
	memset(&addr, 0, sizeof(addr));
	addr.sun_family = AF_UNIX;
	strncpy(addr.sun_path, sock_path, sizeof(addr.sun_path) - 1);

	unlink(sock_path);
	if (bind(state.ipc_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
		close(state.ipc_fd);
		state.ipc_fd = -1;
		return -1;
	}

	listen(state.ipc_fd, 1);
	fprintf(stderr, "[jdesk-libhost] IPC socket: %s\n", sock_path);

	return 0;
}

static void cleanup_ipc(void)
{
	if (state.ipc_fd >= 0) {
		close(state.ipc_fd);
		char sock_path[256];
		snprintf(sock_path, sizeof(sock_path), IPC_SOCKET_PATH_FMT, getpid());
		unlink(sock_path);
	}
}

/* ===========================================================================
 * Argument Parsing
 * ===========================================================================
 */

static struct option long_options[] = {
	{ "library",    required_argument, NULL, 'l' },
	{ "entry",      required_argument, NULL, 'e' },
	{ "role",       required_argument, NULL, 'r' },
	{ "lib-path",   required_argument, NULL, 'p' },
	{ "cross-link", required_argument, NULL, 'x' },
	{ "help",       no_argument,       NULL, 'h' },
	{ "version",    no_argument,       NULL, 'v' },
	{ NULL, 0, NULL, 0 }
};

static void usage(void)
{
	fprintf(stderr,
		"jdesk-libhost v%s — JDesk Library Host Process\n"
		"\n"
		"Loads a shared library and invokes its entry point in a sandboxed process.\n"
		"\n"
		"Usage:\n"
		"  jdesk-libhost --library <path.so> --entry <symbol> [options] [-- args...]\n"
		"\n"
		"Options:\n"
		"  --library, -l <path>    Path to shared library (.so)\n"
		"  --entry, -e <symbol>    Entry point function name\n"
		"  --role, -r <role>       Library role (daemon, service, desktop_app, plugin)\n"
		"  --lib-path, -p <paths>  Colon-separated library search paths\n"
		"  --cross-link, -x <m=r>  Dependency mapping (original=replacement)\n"
		"  --help, -h              Show this help\n"
		"  --version, -v           Show version\n"
		"\n"
		"Everything after '--' is passed as arguments to the library entry point.\n"
		"\n"
		"Copyright (C) 2026 MEARVK LLC\n",
		JDESK_LIBHOST_VERSION);
}

static void parse_lib_paths(const char *paths)
{
	char buf[MAX_LIB_PATH * MAX_LIB_SEARCH_PATHS];
	strncpy(buf, paths, sizeof(buf) - 1);
	buf[sizeof(buf) - 1] = '\0';

	char *saveptr = NULL;
	char *token = strtok_r(buf, ":", &saveptr);
	while (token && state.lib_path_count < MAX_LIB_SEARCH_PATHS) {
		strncpy(state.lib_paths[state.lib_path_count], token, MAX_LIB_PATH - 1);
		state.lib_path_count++;
		token = strtok_r(NULL, ":", &saveptr);
	}
}

static void parse_cross_link(const char *spec)
{
	if (state.cross_link_count >= MAX_CROSS_LINKS) return;

	const char *eq = strchr(spec, '=');
	if (!eq) {
		fprintf(stderr, "[jdesk-libhost] Invalid cross-link spec: %s\n", spec);
		return;
	}

	struct cross_link *cl = &state.cross_links[state.cross_link_count];
	size_t orig_len = (size_t)(eq - spec);
	if (orig_len >= sizeof(cl->original)) orig_len = sizeof(cl->original) - 1;
	memcpy(cl->original, spec, orig_len);
	cl->original[orig_len] = '\0';

	strncpy(cl->replacement, eq + 1, sizeof(cl->replacement) - 1);
	cl->replacement[sizeof(cl->replacement) - 1] = '\0';

	state.cross_link_count++;
}

/* ===========================================================================
 * Main
 * ===========================================================================
 */

int main(int argc, char **argv)
{
	memset(&state, 0, sizeof(state));
	state.ipc_fd = -1;
	state.pid = getpid();
	state.start_time = time(NULL);
	strcpy(state.role, "library");

	/* Parse arguments */
	int opt;
	int entry_argc = 0;
	char **entry_argv = NULL;

	while ((opt = getopt_long(argc, argv, "l:e:r:p:x:hv", long_options, NULL)) != -1) {
		switch (opt) {
		case 'l':
			strncpy(state.library_path, optarg, MAX_LIB_PATH - 1);
			break;
		case 'e':
			strncpy(state.entry_name, optarg, MAX_ENTRY_NAME - 1);
			break;
		case 'r':
			strncpy(state.role, optarg, sizeof(state.role) - 1);
			break;
		case 'p':
			parse_lib_paths(optarg);
			break;
		case 'x':
			parse_cross_link(optarg);
			break;
		case 'h':
			usage();
			return 0;
		case 'v':
			printf("jdesk-libhost %s\n", JDESK_LIBHOST_VERSION);
			return 0;
		default:
			usage();
			return 1;
		}
	}

	/* Everything after '--' goes to the library entry point */
	if (optind < argc) {
		entry_argc = argc - optind;
		entry_argv = &argv[optind];
	}

	/* Validate required arguments */
	if (!state.library_path[0]) {
		fprintf(stderr, "[jdesk-libhost] ERROR: --library is required\n");
		usage();
		return 1;
	}

	/* Banner */
	fprintf(stderr,
		"═══════════════════════════════════════════════════════════════\n"
		"  JDesk Library Host v%s\n"
		"═══════════════════════════════════════════════════════════════\n"
		"  Library: %s\n"
		"  Entry:   %s\n"
		"  Role:    %s\n"
		"  PID:     %d\n"
		"═══════════════════════════════════════════════════════════════\n",
		JDESK_LIBHOST_VERSION,
		state.library_path,
		state.entry_name[0] ? state.entry_name : "(none)",
		state.role,
		state.pid);

	/* Install signal handlers for crash reporting */
	install_signal_handlers();

	/* Setup IPC socket (optional) */
	setup_ipc();

	/* Load the library */
	int ret = load_library();
	if (ret != 0) {
		cleanup_ipc();
		return 1;
	}

	/* Invoke entry point */
	ret = invoke_entry(entry_argc, entry_argv);

	/* Cleanup */
	fprintf(stderr, "[jdesk-libhost] Unloading library...\n");
	if (state.lib_handle) {
		dlclose(state.lib_handle);
		state.lib_handle = NULL;
	}

	cleanup_ipc();

	time_t uptime = time(NULL) - state.start_time;
	fprintf(stderr,
		"═══════════════════════════════════════════════════════════════\n"
		"  JDesk Library Host — Shutdown Complete\n"
		"  Uptime:    %ld seconds\n"
		"  Exit code: %d\n"
		"═══════════════════════════════════════════════════════════════\n",
		uptime, ret);

	return ret;
}
