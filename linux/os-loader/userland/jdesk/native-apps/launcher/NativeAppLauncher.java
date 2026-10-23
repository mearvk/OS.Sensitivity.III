/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDesk Native Application Launcher
 *
 * Detects binary format (ELF, PE, Mach-O, script) and launches under the
 * JVM Memory Proxy with appropriate resource profiles. Provides the bridge
 * between JDesk desktop icons and native binary execution across all three
 * major operating systems.
 *
 * License: GPL-2.0
 */

package us.mearvk.jdesk.launcher;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * NativeAppLauncher — Detects binary format and launches native applications
 * under the JVM Memory Proxy governance layer.
 *
 * Supports:
 *   - ELF (Linux, 32/64-bit) → direct execution
 *   - PE/PE32+ (Windows) → Wine + memory-guard
 *   - Mach-O (macOS) → Darling + memory-guard
 *   - Shebang scripts → interpreter dispatch + memory-guard
 */
public class NativeAppLauncher {

    // =========================================================================
    //  Binary Format Magic Numbers
    // =========================================================================

    private static final byte[] MAGIC_ELF     = { 0x7F, 'E', 'L', 'F' };
    private static final byte[] MAGIC_PE      = { 'M', 'Z' };
    private static final byte[] MAGIC_MACHO64 = { (byte)0xFE, (byte)0xED, (byte)0xFA, (byte)0xCF };
    private static final byte[] MAGIC_MACHO32 = { (byte)0xFE, (byte)0xED, (byte)0xFA, (byte)0xCE };
    private static final byte[] MAGIC_FAT     = { (byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE };
    private static final byte[] MAGIC_SHEBANG = { '#', '!' };

    // =========================================================================
    //  Binary Format Enum
    // =========================================================================

    public enum BinaryFormat {
        ELF_64("ELF 64-bit", "linux"),
        ELF_32("ELF 32-bit", "linux"),
        PE("PE/PE32+", "windows"),
        MACHO_64("Mach-O 64-bit", "macos"),
        MACHO_32("Mach-O 32-bit", "macos"),
        MACHO_FAT("Mach-O Fat/Universal", "macos"),
        SCRIPT("Shebang Script", "linux"),
        UNKNOWN("Unknown", null);

        private final String description;
        private final String platform;

        BinaryFormat(String description, String platform) {
            this.description = description;
            this.platform = platform;
        }

        public String getDescription() { return description; }
        public String getPlatform() { return platform; }
    }

    // =========================================================================
    //  Application Manifest
    // =========================================================================

    /**
     * Represents a .jdesk-app desktop application manifest.
     */
    public static class AppManifest {
        public String name;             // Display name
        public String binaryPath;       // Path to native binary
        public String[] arguments;      // Command-line arguments
        public String iconPath;         // SVG icon path
        public String profile;          // Memory Proxy resource profile name
        public String category;         // Desktop category (office, development, internet, system)
        public boolean startOnDesktop;  // Show icon on desktop at startup
        public boolean startOnPanel;    // Show in panel/taskbar

        // Resource overrides (null = use profile defaults)
        public Long ramSoft;
        public Long ramHard;
        public Integer cpuPercent;
        public Integer threadsMax;
        public Long diskWriteRate;

        public static AppManifest load(Path manifestPath) throws IOException {
            AppManifest m = new AppManifest();
            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(manifestPath)) {
                props.load(is);
            }

            m.name = props.getProperty("name", "Unknown");
            m.binaryPath = props.getProperty("binary");
            m.arguments = props.getProperty("args", "").split("\\s+");
            m.iconPath = props.getProperty("icon");
            m.profile = props.getProperty("profile", "default");
            m.category = props.getProperty("category", "other");
            m.startOnDesktop = Boolean.parseBoolean(props.getProperty("desktop", "true"));
            m.startOnPanel = Boolean.parseBoolean(props.getProperty("panel", "true"));

            String ramS = props.getProperty("ram-soft");
            if (ramS != null) m.ramSoft = parseSize(ramS);
            String ramH = props.getProperty("ram-hard");
            if (ramH != null) m.ramHard = parseSize(ramH);
            String cpu = props.getProperty("cpu");
            if (cpu != null) m.cpuPercent = Integer.parseInt(cpu);
            String threads = props.getProperty("threads");
            if (threads != null) m.threadsMax = Integer.parseInt(threads);
            String diskW = props.getProperty("disk-write");
            if (diskW != null) m.diskWriteRate = parseSize(diskW);

            return m;
        }
    }

    // =========================================================================
    //  Format Detection
    // =========================================================================

    /**
     * Detect the binary format of a file by reading its magic bytes.
     */
    public static BinaryFormat detectFormat(Path binaryPath) throws IOException {
        byte[] header = new byte[64];
        int bytesRead;
        try (InputStream is = Files.newInputStream(binaryPath)) {
            bytesRead = is.read(header);
        }

        if (bytesRead < 4) return BinaryFormat.UNKNOWN;

        // ELF: \x7fELF
        if (matches(header, MAGIC_ELF)) {
            if (bytesRead >= 5) {
                // EI_CLASS: 1 = 32-bit, 2 = 64-bit
                return header[4] == 2 ? BinaryFormat.ELF_64 : BinaryFormat.ELF_32;
            }
            return BinaryFormat.ELF_64;
        }

        // PE: MZ (DOS stub)
        if (matches(header, MAGIC_PE)) {
            return BinaryFormat.PE;
        }

        // Mach-O 64-bit
        if (matches(header, MAGIC_MACHO64)) {
            return BinaryFormat.MACHO_64;
        }

        // Mach-O 32-bit
        if (matches(header, MAGIC_MACHO32)) {
            return BinaryFormat.MACHO_32;
        }

        // Mach-O Fat/Universal
        if (matches(header, MAGIC_FAT)) {
            return BinaryFormat.MACHO_FAT;
        }

        // Shebang script
        if (matches(header, MAGIC_SHEBANG)) {
            return BinaryFormat.SCRIPT;
        }

        return BinaryFormat.UNKNOWN;
    }

    // =========================================================================
    //  Launch Engine
    // =========================================================================

    // =========================================================================
    //  Security: Allowed binary root directories and profile names
    // =========================================================================

    /** Binary must reside under one of these directory prefixes. */
    private static final List<String> ALLOWED_BINARY_ROOTS = List.of(
        "/opt/jdesk/apps/",
        "/usr/bin/",
        "/usr/local/bin/",
        "/bin/"
    );

    /** Profile names must match this pattern — alphanumeric, dash only. */
    private static final java.util.regex.Pattern SAFE_PROFILE =
        java.util.regex.Pattern.compile("^[a-zA-Z0-9\\-]{1,64}$");

    /** App name must be safe for use in thread names and log messages. */
    private static final java.util.regex.Pattern SAFE_NAME =
        java.util.regex.Pattern.compile("^[\\w\\-. ]{1,128}$");

    /**
     * Validate that a binary path is canonical and under an allowed root.
     * Prevents path traversal and manifest injection attacks.
     */
    private static Path validateBinaryPath(String rawPath) throws IOException {
        if (rawPath == null || rawPath.isBlank()) {
            throw new SecurityException("Binary path is null or empty");
        }
        // Resolve to real absolute path — eliminates symlink chains and ../
        Path canonical;
        try {
            canonical = Path.of(rawPath).toRealPath();
        } catch (IOException e) {
            throw new FileNotFoundException("Binary not found or unresolvable: " + rawPath);
        }
        String canonicalStr = canonical.toString();
        for (String root : ALLOWED_BINARY_ROOTS) {
            if (canonicalStr.startsWith(root)) {
                return canonical;
            }
        }
        throw new SecurityException(
            "Binary path '" + canonicalStr + "' is outside allowed roots: " + ALLOWED_BINARY_ROOTS);
    }

    /**
     * Validate a profile name — must be alphanumeric + dash only.
     */
    private static String validateProfile(String profile) {
        if (profile == null || !SAFE_PROFILE.matcher(profile).matches()) {
            System.err.printf("[JDesk] Warning: unsafe profile name '%s' — using 'default'%n", profile);
            return "default";
        }
        return profile;
    }

    /**
     * Validate app name — safe for use in thread names and env vars.
     */
    private static String validateName(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            return "Unknown";
        }
        return name;
    }

    /**
     * Launch a native application under JVM Memory Proxy governance.
     *
     * Security: binary path is validated against allowed roots before exec.
     * Profile name is validated against a safe pattern.
     * Manifest fields are sanitized before use in command construction.
     *
     * @param manifest Application manifest
     * @return Process handle for the launched application
     */
    public static Process launch(AppManifest manifest) throws IOException {
        // Validate and canonicalize binary path — prevents path traversal
        Path binaryPath = validateBinaryPath(manifest.binaryPath);

        // Sanitize name and profile before use in command / env
        String safeName    = validateName(manifest.name);
        String safeProfile = validateProfile(manifest.profile);

        BinaryFormat format = detectFormat(binaryPath);
        List<String> command = buildCommand(format, manifest, binaryPath.toString(), safeProfile);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        // Env values: name is validated, profile is validated
        pb.environment().put("JDESK_APP_NAME",    safeName);
        pb.environment().put("JDESK_APP_PROFILE", safeProfile);

        System.out.printf("[JDesk] Launching: %s (%s)%n", safeName, format.getDescription());
        System.out.printf("[JDesk] Profile: %s | RAM: %s/%s | CPU: %s%%%n",
                safeProfile,
                formatSize(manifest.ramSoft),
                formatSize(manifest.ramHard),
                manifest.cpuPercent != null ? manifest.cpuPercent : "default");

        return pb.start();
    }

    /**
     * Build the full command line based on binary format.
     */
    /**
     * Build the full command line based on binary format.
     * Uses pre-validated binaryPath and safeProfile — never raw manifest values.
     */
    private static List<String> buildCommand(BinaryFormat format, AppManifest manifest,
                                             String validatedPath, String safeProfile) {
        List<String> cmd = new ArrayList<>();

        // JVM memory-guard prefix
        cmd.add("java");
        cmd.add("-memory-guard");

        // Resource profile — validated safe string
        cmd.add("-Xguard:profile=" + safeProfile);

        // Resource overrides — numeric values, no injection possible
        if (manifest.ramSoft != null) {
            cmd.add("-Xguard:ram-soft=" + formatSizeFlag(manifest.ramSoft));
        }
        if (manifest.ramHard != null) {
            cmd.add("-Xguard:ram-hard=" + formatSizeFlag(manifest.ramHard));
        }
        if (manifest.cpuPercent != null) {
            cmd.add("-Xguard:cpu=" + manifest.cpuPercent);
        }
        if (manifest.threadsMax != null) {
            cmd.add("-Xguard:threads=" + manifest.threadsMax);
        }
        if (manifest.diskWriteRate != null) {
            cmd.add("-Xguard:disk-write=" + formatSizeFlag(manifest.diskWriteRate));
        }

        // Status reporting to JDesk panel
        cmd.add("-Xguard:status=5s");

        // Platform-specific execution wrapper — use validated canonical path
        switch (format) {
            case ELF_64:
            case ELF_32:
            case SCRIPT:
                cmd.add(validatedPath);
                break;
            case PE:
                cmd.add("wine");
                cmd.add(validatedPath);
                break;
            case MACHO_64:
            case MACHO_32:
            case MACHO_FAT:
                cmd.add("darling");
                cmd.add("shell");
                cmd.add(validatedPath);
                break;
            default:
                cmd.add(validatedPath);
                break;
        }

        // Arguments — reject shell metacharacters; each arg is discrete (no shell parsing)
        if (manifest.arguments != null) {
            for (String arg : manifest.arguments) {
                if (arg != null && !arg.isBlank()) {
                    String safeArg = arg.trim();
                    if (!safeArg.contains(";") && !safeArg.contains("&")
                            && !safeArg.contains("|") && !safeArg.contains("`")
                            && !safeArg.contains("$") && !safeArg.contains(">")
                            && !safeArg.contains("<") && !safeArg.contains("\n")) {
                        cmd.add(safeArg);
                    } else {
                        System.err.printf("[JDesk] Warning: rejected unsafe argument '%s'%n", safeArg);
                    }
                }
            }
        }

        return cmd;
    }


    // =========================================================================
    //  Desktop Icon Registration
    // =========================================================================

    private static final Path MANIFESTS_DIR = Path.of("/opt/jdesk/manifests");
    private static final Path ICONS_DIR = Path.of("/opt/jdesk/icons");

    /**
     * Load all registered desktop applications (for startup icon placement).
     */
    public static List<AppManifest> loadAllManifests() throws IOException {
        List<AppManifest> apps = new ArrayList<>();

        if (!Files.isDirectory(MANIFESTS_DIR)) {
            return apps;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MANIFESTS_DIR, "*.jdesk-app")) {
            for (Path entry : stream) {
                try {
                    AppManifest m = AppManifest.load(entry);
                    apps.add(m);
                } catch (Exception e) {
                    System.err.printf("[JDesk] Warning: Failed to load manifest %s: %s%n",
                            entry.getFileName(), e.getMessage());
                }
            }
        }

        return apps;
    }

    /**
     * Get applications that should appear on the desktop at startup.
     */
    public static List<AppManifest> getDesktopApps() throws IOException {
        List<AppManifest> all = loadAllManifests();
        List<AppManifest> desktop = new ArrayList<>();
        for (AppManifest m : all) {
            if (m.startOnDesktop) desktop.add(m);
        }
        return desktop;
    }

    /**
     * Get applications that should appear in the panel/taskbar.
     */
    public static List<AppManifest> getPanelApps() throws IOException {
        List<AppManifest> all = loadAllManifests();
        List<AppManifest> panel = new ArrayList<>();
        for (AppManifest m : all) {
            if (m.startOnPanel) panel.add(m);
        }
        return panel;
    }

    // =========================================================================
    //  Utility Methods
    // =========================================================================

    private static boolean matches(byte[] data, byte[] magic) {
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }

    /**
     * Parse human-readable size string (e.g., "512m", "4g", "100k").
     */
    private static long parseSize(String s) {
        s = s.trim().toLowerCase();
        long multiplier = 1;
        if (s.endsWith("k")) { multiplier = 1024L; s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("m")) { multiplier = 1024L * 1024; s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("g")) { multiplier = 1024L * 1024 * 1024; s = s.substring(0, s.length() - 1); }
        return Long.parseLong(s) * multiplier;
    }

    private static String formatSize(Long bytes) {
        if (bytes == null) return "default";
        if (bytes >= 1024L * 1024 * 1024) return (bytes / (1024L * 1024 * 1024)) + " GB";
        if (bytes >= 1024L * 1024) return (bytes / (1024L * 1024)) + " MB";
        if (bytes >= 1024L) return (bytes / 1024L) + " KB";
        return bytes + " B";
    }

    private static String formatSizeFlag(Long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return (bytes / (1024L * 1024 * 1024)) + "g";
        if (bytes >= 1024L * 1024) return (bytes / (1024L * 1024)) + "m";
        return bytes.toString();
    }

    // =========================================================================
    //  Library Co-Linking Support (.so / .dll / .dylib)
    // =========================================================================

    /**
     * Launch a shared library (.so, .dll, .dylib) as a runnable program.
     * Delegates to the LibraryLinker co-linking engine for format detection,
     * dependency resolution, entry point discovery, and execution.
     *
     * @param manifest Application manifest (binary path points to .so/.dll/.dylib)
     * @return Process handle for the launched library host
     */
    public static Process launchLibrary(AppManifest manifest) throws IOException {
        Path libPath = Path.of(manifest.binaryPath);
        String profile = validateProfile(manifest.profile);

        System.out.printf("[JDesk] Library launch: %s (profile: %s)%n",
                libPath.getFileName(), profile);

        // Inspect the library (format, symbols, dependencies, entry point)
        LibraryLinker.LibraryDescriptor desc = LibraryLinker.inspect(libPath);

        // Override entry point if manifest specifies one
        // (manifest can declare entry=myCustomInit in properties)
        // Otherwise use the auto-discovered entry point

        // Build args from manifest
        String[] args = manifest.arguments;

        // Launch via LibraryLinker co-linking engine
        return LibraryLinker.load(desc, profile, args);
    }

    /**
     * Detect if a file is a shared library (as opposed to an executable).
     * Used by the desktop icon handler to route to the correct launcher.
     */
    public static boolean isSharedLibrary(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".so") || name.contains(".so.") ||
            name.endsWith(".dll") || name.endsWith(".dylib")) {
            return true;
        }
        // Check magic bytes for ELF ET_DYN without .so extension
        try {
            byte[] header = new byte[20];
            try (InputStream is = java.nio.file.Files.newInputStream(path)) {
                int read = is.read(header);
                if (read >= 18 && header[0] == 0x7F && header[1] == 'E' &&
                    header[2] == 'L' && header[3] == 'F') {
                    int eType = (header[16] & 0xFF) | ((header[17] & 0xFF) << 8);
                    return eType == 3; // ET_DYN = shared object
                }
            }
        } catch (IOException ignored) {}
        return false;
    }

    // =========================================================================
    //  Main (for testing / CLI usage)
    // =========================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: NativeAppLauncher <manifest.jdesk-app>");
            System.out.println("       NativeAppLauncher --detect <binary>");
            System.out.println("       NativeAppLauncher --list");
            System.out.println("       NativeAppLauncher --lib-inspect <library.so|.dll>");
            System.out.println("       NativeAppLauncher --lib-load <library.so|.dll> [args...]");
            System.exit(1);
        }

        if ("--detect".equals(args[0]) && args.length >= 2) {
            Path p = Path.of(args[1]);
            BinaryFormat fmt = detectFormat(p);
            boolean isLib = isSharedLibrary(p);
            System.out.printf("File: %s%nFormat: %s%nPlatform: %s%nShared Library: %s%n",
                    p, fmt.getDescription(), fmt.getPlatform(), isLib);
            return;
        }

        if ("--list".equals(args[0])) {
            List<AppManifest> apps = loadAllManifests();
            System.out.printf("Registered applications: %d%n%n", apps.size());
            for (AppManifest m : apps) {
                Path bp = Path.of(m.binaryPath);
                BinaryFormat fmt = Files.exists(bp) ? detectFormat(bp) : BinaryFormat.UNKNOWN;
                boolean isLib = Files.exists(bp) && isSharedLibrary(bp);
                System.out.printf("  %-20s  %-15s  %s  %s%n", m.name, fmt.getDescription(),
                        isLib ? "[LIB]" : "[EXE]", m.binaryPath);
            }
            return;
        }

        if ("--lib-inspect".equals(args[0]) && args.length >= 2) {
            // Delegate to LibraryLinker for full inspection
            LibraryLinker.main(new String[]{"--inspect", args[1]});
            return;
        }

        if ("--lib-load".equals(args[0]) && args.length >= 2) {
            String[] loadArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : null;
            LibraryLinker.main(new String[]{"--load", args[1]});
            return;
        }

        // Load manifest and launch (auto-detect library vs executable)
        AppManifest manifest = AppManifest.load(Path.of(args[0]));
        Path binaryPath = Path.of(manifest.binaryPath);

        Process proc;
        if (Files.exists(binaryPath) && isSharedLibrary(binaryPath)) {
            System.out.printf("[JDesk] Detected shared library — using LibraryLinker%n");
            proc = launchLibrary(manifest);
        } else {
            proc = launch(manifest);
        }

        if (proc != null) {
            proc.waitFor();
            System.out.printf("[JDesk] %s exited with code %d%n", manifest.name, proc.exitValue());
        }
    }
}
