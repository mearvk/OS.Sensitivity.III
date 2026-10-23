/* SPDX-License-Identifier: GPL-2.0 */
/*
 * jdesk_launcher.c — Native ELF Binary Launcher for JDesk Desktop Environment
 *
 * A compiled C binary that discovers the JVM, sets up the environment
 * (classpath, module path, JVM flags), and exec()s into JDesk.
 *
 * Why a native binary instead of a shell script?
 *   - Faster startup (no shell interpreter overhead)
 *   - Proper process name in ps/top (shows "jdesk" not "bash" or "java")
 *   - Can be setuid/setcap if needed for display access
 *   - Integrates with desktop .desktop files cleanly
 *   - Detects hardware (CPU, display) natively before JVM launch
 *   - Validates environment and gives clear diagnostics
 *
 * Build:
 *   gcc -O2 -Wall -Wextra -o jdesk-bin jdesk_launcher.c -lX11
 *
 * Install:
 *   sudo install -m 755 jdesk-bin /usr/local/bin/jdesk
 *
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 */

/* _GNU_SOURCE defined via -D_GNU_SOURCE on compiler command line */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/utsname.h>
#include <sys/sysinfo.h>
#include <dirent.h>
#include <stdbool.h>
#include <cpuid.h>
#include <X11/Xlib.h>

/* ===========================================================================
 * Constants
 * ===========================================================================
 */

#define JDESK_VERSION           "1.0.0"
#define JDESK_EDITION           "Galactic Cherry Marvell Edition 98"
#define JDESK_MAIN_CLASS        "us.mearvk.jdesk.JDeskApplication"

#define MAX_PATH                4096
#define MAX_ARGS                64
#define MAX_CLASSPATH           8192
#define MAX_JVM_CANDIDATES      8

/* JVM search paths (in priority order) */
static const char *jvm_search_paths[] = {
    "/usr/lib/jvm/java-28-openjdk-amd64",
    "/usr/lib/jvm/java-25-openjdk-amd64",
    "/usr/lib/jvm/java-21-openjdk-amd64",
    "/usr/lib/jvm/java-17-openjdk-amd64",
    "/usr/lib/jvm/default-java",
    "/opt/java/latest",
    "/opt/jdk",
    NULL
};

/* JavaFX module paths */
static const char *javafx_search_paths[] = {
    "/usr/share/openjfx/lib",
    "/usr/lib/jvm/javafx-sdk/lib",
    "/opt/javafx/lib",
    "/opt/openjfx/lib",
    NULL
};

/* JDesk installation search paths */
static const char *jdesk_search_paths[] = {
    "/opt/jdesk",
    "/usr/local/share/jdesk",
    "/usr/share/jdesk",
    NULL
};

/* ===========================================================================
 * Structures
 * ===========================================================================
 */

struct jdesk_config {
    char java_bin[MAX_PATH];        /* Path to java binary */
    char java_home[MAX_PATH];       /* JAVA_HOME */
    char javafx_path[MAX_PATH];     /* JavaFX module path */
    char jdesk_home[MAX_PATH];      /* JDesk installation */
    char classpath[MAX_CLASSPATH];  /* Full classpath */
    bool windowed;                  /* --windowed mode */
    bool verbose;                   /* --verbose output */
    bool wayland;                   /* Wayland session detected */
    bool has_display;               /* X11 display available */
    bool has_avx2;                  /* CPU supports AVX2 */
    int  java_version;              /* Detected Java major version */
};

/* ===========================================================================
 * Utility Functions
 * ===========================================================================
 */

static bool file_exists(const char *path)
{
    struct stat st;
    return stat(path, &st) == 0;
}

static bool is_executable(const char *path)
{
    return access(path, X_OK) == 0;
}

static bool is_directory(const char *path)
{
    struct stat st;
    return stat(path, &st) == 0 && S_ISDIR(st.st_mode);
}

/* Get the directory containing the launcher binary itself */
static bool get_self_dir(char *buf, size_t bufsz)
{
    ssize_t len = readlink("/proc/self/exe", buf, bufsz - 1);
    if (len <= 0)
        return false;
    buf[len] = '\0';

    /* Strip the binary name to get directory */
    char *slash = strrchr(buf, '/');
    if (slash)
        *slash = '\0';
    return true;
}

/* ===========================================================================
 * JVM Discovery
 * ===========================================================================
 */

static bool discover_java(struct jdesk_config *cfg)
{
    char path[MAX_PATH];

    /* 1. Check JAVA_HOME environment variable */
    const char *java_home = getenv("JAVA_HOME");
    if (java_home && java_home[0]) {
        snprintf(path, sizeof(path), "%s/bin/java", java_home);
        if (is_executable(path)) {
            strncpy(cfg->java_bin, path, MAX_PATH - 1);
            strncpy(cfg->java_home, java_home, MAX_PATH - 1);
            return true;
        }
    }

    /* 2. Search known JVM installation paths */
    for (int i = 0; jvm_search_paths[i]; i++) {
        snprintf(path, sizeof(path), "%s/bin/java", jvm_search_paths[i]);
        if (is_executable(path)) {
            strncpy(cfg->java_bin, path, MAX_PATH - 1);
            strncpy(cfg->java_home, jvm_search_paths[i], MAX_PATH - 1);
            return true;
        }
    }

    /* 3. Fallback: find java in PATH */
    const char *env_path = getenv("PATH");
    if (env_path) {
        char *path_copy = strdup(env_path);
        char *dir = strtok(path_copy, ":");
        while (dir) {
            snprintf(path, sizeof(path), "%s/java", dir);
            if (is_executable(path)) {
                strncpy(cfg->java_bin, path, MAX_PATH - 1);
                /* Resolve JAVA_HOME from bin/java path */
                char *bin = strstr(path, "/bin/java");
                if (bin) {
                    *bin = '\0';
                    strncpy(cfg->java_home, path, MAX_PATH - 1);
                }
                free(path_copy);
                return true;
            }
            dir = strtok(NULL, ":");
        }
        free(path_copy);
    }

    return false;
}

/* Detect Java version from the binary */
static int detect_java_version(const char *java_bin)
{
    char cmd[MAX_PATH + 32];
    snprintf(cmd, sizeof(cmd), "%s -version 2>&1", java_bin);

    FILE *fp = popen(cmd, "r");
    if (!fp)
        return 0;

    char line[256];
    int version = 0;
    if (fgets(line, sizeof(line), fp)) {
        /* Parse: openjdk version "28.0.1" or "17.0.2" etc */
        char *quote = strchr(line, '"');
        if (quote) {
            version = atoi(quote + 1);
        }
    }
    pclose(fp);
    return version;
}

/* ===========================================================================
 * JavaFX Discovery
 * ===========================================================================
 */

static bool discover_javafx(struct jdesk_config *cfg)
{
    char path[MAX_PATH];

    /* Check standard paths */
    for (int i = 0; javafx_search_paths[i]; i++) {
        snprintf(path, sizeof(path), "%s/javafx.controls.jar",
                 javafx_search_paths[i]);
        if (file_exists(path)) {
            strncpy(cfg->javafx_path, javafx_search_paths[i], MAX_PATH - 1);
            return true;
        }
        /* Also check for module-info style (modular JFX) */
        snprintf(path, sizeof(path), "%s/javafx.base.jar",
                 javafx_search_paths[i]);
        if (file_exists(path)) {
            strncpy(cfg->javafx_path, javafx_search_paths[i], MAX_PATH - 1);
            return true;
        }
    }

    /* Check inside JAVA_HOME */
    if (cfg->java_home[0]) {
        snprintf(path, sizeof(path), "%s/lib/javafx.controls.jar",
                 cfg->java_home);
        if (file_exists(path)) {
            snprintf(cfg->javafx_path, MAX_PATH, "%s/lib", cfg->java_home);
            return true;
        }
    }

    return false;
}

/* ===========================================================================
 * JDesk Installation Discovery
 * ===========================================================================
 */

static bool discover_jdesk(struct jdesk_config *cfg)
{
    char path[MAX_PATH];

    /* 1. Check JDESK_HOME environment variable */
    const char *jdesk_home = getenv("JDESK_HOME");
    if (jdesk_home && jdesk_home[0] && is_directory(jdesk_home)) {
        strncpy(cfg->jdesk_home, jdesk_home, MAX_PATH - 1);
        return true;
    }

    /* 2. Check standard installation paths */
    for (int i = 0; jdesk_search_paths[i]; i++) {
        if (is_directory(jdesk_search_paths[i])) {
            strncpy(cfg->jdesk_home, jdesk_search_paths[i], MAX_PATH - 1);
            return true;
        }
    }

    /* 3. Check relative to our own binary (development mode) */
    char self_dir[MAX_PATH];
    if (get_self_dir(self_dir, sizeof(self_dir))) {
        /* We might be in native/linux/ — go up two levels to jdesk root */
        snprintf(path, sizeof(path), "%s/../../src", self_dir);
        if (is_directory(path)) {
            snprintf(cfg->jdesk_home, MAX_PATH, "%s/../..", self_dir);
            /* Normalize */
            char *resolved = realpath(cfg->jdesk_home, NULL);
            if (resolved) {
                strncpy(cfg->jdesk_home, resolved, MAX_PATH - 1);
                free(resolved);
            }
            return true;
        }
        /* Or we might be directly in the jdesk directory */
        snprintf(path, sizeof(path), "%s/src", self_dir);
        if (is_directory(path)) {
            strncpy(cfg->jdesk_home, self_dir, MAX_PATH - 1);
            return true;
        }
    }

    return false;
}

/* ===========================================================================
 * Classpath Construction
 * ===========================================================================
 */

static void build_classpath(struct jdesk_config *cfg)
{
    char path[MAX_PATH];

    cfg->classpath[0] = '\0';

    /* Check for installed jar */
    snprintf(path, sizeof(path), "%s/lib/jdesk.jar", cfg->jdesk_home);
    if (file_exists(path)) {
        strncat(cfg->classpath, path, MAX_CLASSPATH - strlen(cfg->classpath) - 1);
        return;
    }

    /* Development mode: compiled classes directory */
    snprintf(path, sizeof(path), "%s/classes", cfg->jdesk_home);
    if (is_directory(path)) {
        strncat(cfg->classpath, path, MAX_CLASSPATH - strlen(cfg->classpath) - 1);
        return;
    }

    /* Fall back to source tree (will need compilation) */
    snprintf(path, sizeof(path), "%s/src", cfg->jdesk_home);
    if (is_directory(path)) {
        /* Attempt compilation */
        fprintf(stderr, "[JDesk] No compiled classes found. Compiling from source...\n");

        char compile_cmd[MAX_PATH * 3];
        snprintf(path, sizeof(path), "%s/classes", cfg->jdesk_home);
        mkdir(path, 0755);

        if (cfg->javafx_path[0]) {
            snprintf(compile_cmd, sizeof(compile_cmd),
                     "%s/../bin/javac --release 17 --module-path %s "
                     "--add-modules javafx.controls,javafx.graphics,javafx.web "
                     "-sourcepath %s/src -d %s/classes "
                     "$(find %s/src -name '*.java')",
                     cfg->java_bin, cfg->javafx_path,
                     cfg->jdesk_home, cfg->jdesk_home, cfg->jdesk_home);
        } else {
            snprintf(compile_cmd, sizeof(compile_cmd),
                     "%s/../bin/javac --release 17 "
                     "-sourcepath %s/src -d %s/classes "
                     "$(find %s/src -name '*.java')",
                     cfg->java_bin, cfg->jdesk_home, cfg->jdesk_home,
                     cfg->jdesk_home);
        }

        int rc = system(compile_cmd);
        if (rc != 0) {
            fprintf(stderr, "[JDesk] ERROR: Compilation failed (exit %d)\n", rc);
            return;
        }
        fprintf(stderr, "[JDesk] Compiled successfully.\n");

        snprintf(path, sizeof(path), "%s/classes", cfg->jdesk_home);
        strncat(cfg->classpath, path, MAX_CLASSPATH - strlen(cfg->classpath) - 1);
    }
}

/* ===========================================================================
 * Hardware & Environment Detection
 * ===========================================================================
 */

static bool detect_avx2(void)
{
    unsigned int eax, ebx, ecx, edx;
    if (__get_cpuid_count(7, 0, &eax, &ebx, &ecx, &edx)) {
        return (ebx & (1 << 5)) != 0;  /* AVX2 bit */
    }
    return false;
}

static bool detect_display(void)
{
    Display *dpy = XOpenDisplay(NULL);
    if (dpy) {
        XCloseDisplay(dpy);
        return true;
    }
    return false;
}

static bool detect_wayland(void)
{
    const char *session = getenv("XDG_SESSION_TYPE");
    return session && strcmp(session, "wayland") == 0;
}

static bool detect_existing_desktop(void)
{
    return getenv("XDG_CURRENT_DESKTOP") != NULL ||
           getenv("DESKTOP_SESSION") != NULL;
}

/* ===========================================================================
 * Argument Parsing
 * ===========================================================================
 */

static void print_usage(void)
{
    printf("JDesk — %s\n", JDESK_EDITION);
    printf("Version: %s\n\n", JDESK_VERSION);
    printf("Usage: jdesk [OPTIONS]\n\n");
    printf("Options:\n");
    printf("  --windowed, -w    Run in a window (inside existing desktop)\n");
    printf("  --verbose, -v     Show detailed startup information\n");
    printf("  --version         Show version and exit\n");
    printf("  --check           Check environment and exit (don't launch)\n");
    printf("  --help, -h        Show this help\n\n");
    printf("Environment:\n");
    printf("  JDESK_HOME        JDesk installation directory (default: /opt/jdesk)\n");
    printf("  JAVA_HOME         Java installation directory\n");
    printf("  DISPLAY           X11 display (default: :0)\n\n");
    printf("Install: sudo install -m 755 jdesk-bin /usr/local/bin/jdesk\n");
}

static void print_version(void)
{
    printf("jdesk %s (%s)\n", JDESK_VERSION, JDESK_EDITION);
    printf("Copyright (C) 2026 MEARVK LLC\n");
}

/* ===========================================================================
 * Banner
 * ===========================================================================
 */

static void print_banner(const struct jdesk_config *cfg)
{
    struct sysinfo si;
    sysinfo(&si);
    unsigned long ram_mb = si.totalram / (1024 * 1024);

    printf("═══════════════════════════════════════════════════════════════\n");
    printf("  JDesk — %s\n", JDESK_EDITION);
    printf("  Version:  %s\n", JDESK_VERSION);
    printf("  Theme:    Cool White\n");
    printf("  Mode:     %s\n", cfg->windowed ? "windowed" : "full-screen");
    printf("  Java:     %s (version %d)\n", cfg->java_bin, cfg->java_version);
    printf("  JavaFX:   %s\n", cfg->javafx_path[0] ? cfg->javafx_path : "system classpath");
    printf("  Display:  %s%s\n",
           cfg->wayland ? "Wayland (XWayland bridge)" : "X11",
           cfg->has_display ? "" : " [NOT AVAILABLE]");
    printf("  CPU:      %s\n", cfg->has_avx2 ? "AVX2 (optimal)" : "SSE4.2 (baseline)");
    printf("  RAM:      %lu MB\n", ram_mb);
    printf("═══════════════════════════════════════════════════════════════\n");
}

/* ===========================================================================
 * Environment Check (--check mode)
 * ===========================================================================
 */

static int run_check(const struct jdesk_config *cfg)
{
    int issues = 0;

    printf("\n[JDesk] Environment Check\n");
    printf("─────────────────────────────────────────────────\n");

    /* Java */
    if (cfg->java_bin[0]) {
        printf("  ✓ Java:      %s (version %d)\n", cfg->java_bin, cfg->java_version);
        if (cfg->java_version < 17) {
            printf("    ⚠ WARNING: Java 17+ recommended (have %d)\n", cfg->java_version);
            issues++;
        }
    } else {
        printf("  ✗ Java:      NOT FOUND\n");
        issues++;
    }

    /* JavaFX */
    if (cfg->javafx_path[0])
        printf("  ✓ JavaFX:    %s\n", cfg->javafx_path);
    else {
        printf("  ✗ JavaFX:    NOT FOUND (install openjfx)\n");
        issues++;
    }

    /* JDesk installation */
    if (cfg->jdesk_home[0])
        printf("  ✓ JDesk:     %s\n", cfg->jdesk_home);
    else {
        printf("  ✗ JDesk:     NOT FOUND\n");
        issues++;
    }

    /* Display */
    if (cfg->has_display)
        printf("  ✓ Display:   %s\n", cfg->wayland ? "Wayland" : "X11");
    else {
        printf("  ✗ Display:   NOT AVAILABLE (no X11 connection)\n");
        issues++;
    }

    /* CPU */
    printf("  ✓ CPU:       %s\n", cfg->has_avx2 ? "AVX2 supported" : "SSE baseline");

    /* Native library */
    char libpath[MAX_PATH];
    snprintf(libpath, sizeof(libpath), "%s/native/linux/libjdesk.so", cfg->jdesk_home);
    if (file_exists(libpath))
        printf("  ✓ libjdesk:  %s\n", libpath);
    else {
        snprintf(libpath, sizeof(libpath), "/usr/local/lib/libjdesk.so");
        if (file_exists(libpath))
            printf("  ✓ libjdesk:  %s\n", libpath);
        else
            printf("  ⚠ libjdesk:  Not found (build with: make -C native/linux)\n");
    }

    printf("─────────────────────────────────────────────────\n");
    if (issues == 0)
        printf("  Result: READY ✓ (all checks passed)\n");
    else
        printf("  Result: %d issue(s) found\n", issues);
    printf("\n");

    return issues;
}

/* ===========================================================================
 * Main: Assemble and Execute
 * ===========================================================================
 */

int main(int argc, char *argv[])
{
    struct jdesk_config cfg;
    bool check_only = false;

    memset(&cfg, 0, sizeof(cfg));

    /* Parse arguments */
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--windowed") == 0 || strcmp(argv[i], "-w") == 0) {
            cfg.windowed = true;
        } else if (strcmp(argv[i], "--verbose") == 0 || strcmp(argv[i], "-v") == 0) {
            cfg.verbose = true;
        } else if (strcmp(argv[i], "--help") == 0 || strcmp(argv[i], "-h") == 0) {
            print_usage();
            return 0;
        } else if (strcmp(argv[i], "--version") == 0) {
            print_version();
            return 0;
        } else if (strcmp(argv[i], "--check") == 0) {
            check_only = true;
        }
    }

    /* Detect hardware and environment */
    cfg.has_avx2 = detect_avx2();
    cfg.wayland = detect_wayland();
    cfg.has_display = detect_display();

    /* If no explicit --windowed and running inside a desktop, default to windowed */
    if (!cfg.windowed && detect_existing_desktop()) {
        cfg.windowed = true;
        if (cfg.verbose)
            fprintf(stderr, "[JDesk] Detected existing desktop session — windowed mode.\n");
    }

    /* Discover Java */
    if (!discover_java(&cfg)) {
        fprintf(stderr, "[JDesk] ERROR: Java not found.\n");
        fprintf(stderr, "  Install OpenJDK 17+: sudo apt install openjdk-21-jdk\n");
        fprintf(stderr, "  Or set JAVA_HOME environment variable.\n");
        return 1;
    }
    cfg.java_version = detect_java_version(cfg.java_bin);

    /* Discover JavaFX */
    discover_javafx(&cfg);

    /* Discover JDesk installation */
    if (!discover_jdesk(&cfg)) {
        fprintf(stderr, "[JDesk] ERROR: JDesk installation not found.\n");
        fprintf(stderr, "  Set JDESK_HOME or install to /opt/jdesk.\n");
        return 1;
    }

    /* Check mode: report and exit */
    if (check_only) {
        return run_check(&cfg);
    }

    /* Verify display is available */
    if (!cfg.has_display) {
        fprintf(stderr, "[JDesk] ERROR: No display available.\n");
        fprintf(stderr, "  Ensure X11 is running or set DISPLAY=:0\n");
        return 1;
    }

    /* Build classpath */
    build_classpath(&cfg);
    if (cfg.classpath[0] == '\0') {
        fprintf(stderr, "[JDesk] ERROR: No classes found. Build JDesk first.\n");
        return 1;
    }

    /* Print banner */
    print_banner(&cfg);

    /* Wayland environment adjustments */
    if (cfg.wayland) {
        setenv("GDK_BACKEND", "x11", 1);
        if (cfg.verbose)
            fprintf(stderr, "[JDesk] Wayland detected — using XWayland bridge.\n");
    }

    /* Set java.library.path for libjdesk.so discovery */
    char libpath[MAX_PATH];
    snprintf(libpath, sizeof(libpath), "%s/native/linux", cfg.jdesk_home);
    if (!is_directory(libpath))
        snprintf(libpath, sizeof(libpath), "/usr/local/lib");

    /* ==================================================================
     * Build exec() argument vector
     * ==================================================================
     */

    char *exec_argv[MAX_ARGS];
    int ai = 0;

    exec_argv[ai++] = (char *)"jdesk";  /* argv[0]: process name */

    /* JVM flags */
    exec_argv[ai++] = (char *)"-Dprism.order=sw";
    exec_argv[ai++] = (char *)"-Djava.awt.headless=false";
    exec_argv[ai++] = (char *)"-Djavafx.verbose=false";

    char lib_path_flag[MAX_PATH + 32];
    snprintf(lib_path_flag, sizeof(lib_path_flag),
             "-Djava.library.path=%s:/usr/local/lib", libpath);
    exec_argv[ai++] = lib_path_flag;

    /* Wayland JDK flags */
    if (cfg.wayland)
        exec_argv[ai++] = (char *)"-Djdk.gtk.version=3";

    /* JavaFX module path and modules */
    char module_path_flag[MAX_PATH + 16];
    if (cfg.javafx_path[0]) {
        snprintf(module_path_flag, sizeof(module_path_flag),
                 "--module-path=%s", cfg.javafx_path);
        exec_argv[ai++] = module_path_flag;
        exec_argv[ai++] = (char *)"--add-modules=javafx.controls,javafx.graphics,javafx.swing,javafx.web";
    }

    /* Classpath */
    char cp_flag[MAX_CLASSPATH + 8];
    snprintf(cp_flag, sizeof(cp_flag), "-cp");
    exec_argv[ai++] = cp_flag;
    exec_argv[ai++] = cfg.classpath;

    /* Main class */
    exec_argv[ai++] = (char *)JDESK_MAIN_CLASS;

    /* Application arguments */
    if (cfg.windowed)
        exec_argv[ai++] = (char *)"--windowed";

    /* Pass through any remaining user arguments */
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--windowed") == 0 || strcmp(argv[i], "-w") == 0 ||
            strcmp(argv[i], "--verbose") == 0 || strcmp(argv[i], "-v") == 0 ||
            strcmp(argv[i], "--check") == 0)
            continue;
        if (ai < MAX_ARGS - 1)
            exec_argv[ai++] = argv[i];
    }

    exec_argv[ai] = NULL;

    /* Verbose: show full command */
    if (cfg.verbose) {
        fprintf(stderr, "\n[JDesk] exec: %s", cfg.java_bin);
        for (int i = 1; exec_argv[i]; i++)
            fprintf(stderr, " %s", exec_argv[i]);
        fprintf(stderr, "\n\n");
    }

    /* ==================================================================
     * exec() into the JVM — this does not return on success
     * ==================================================================
     */

    execv(cfg.java_bin, exec_argv);

    /* If we get here, exec failed */
    fprintf(stderr, "[JDesk] FATAL: exec(%s) failed: %s\n",
            cfg.java_bin, strerror(errno));
    return 127;
}
