/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDesk Library Linker — Co-Linking Engine for .so and .dll runnables
 *
 * Enables JDesk to load, inspect, and execute shared libraries (.so, .dll)
 * as first-class runnable programs: daemons, services, or desktop applications.
 *
 * The linker provides:
 *   - Format detection and symbol inspection for ELF .so and PE .dll
 *   - Entry point discovery (exported main, DllMain, plugin_init, service_main, etc.)
 *   - Cross-reference assumption engine for dependency resolution
 *   - Static and dynamic co-linking to OS-level library handlers
 *   - JNI bridge for in-process loading (when library is JDesk-native)
 *   - Out-of-process host for untrusted libraries (sandboxed)
 *
 * Supported formats:
 *   .so  (Linux ELF shared object)  → dlopen/dlsym or host process
 *   .dll (Windows PE DLL)           → Wine + LoadLibrary or host
 *   .dylib (macOS dynamic library)  → Darling + dlopen or host
 *
 * Open-source components used:
 *   - libelf (elfutils, GPL-2.0/LGPL-3.0) — ELF parsing and symbol enumeration
 *   - pe-parse (Trail of Bits, MIT) — PE/COFF DLL parsing
 *   - Wine (LGPL-2.1) — Windows DLL execution layer
 *   - Darling (GPL-3.0) — macOS dylib execution layer
 *
 * License: GPL-2.0
 */

package us.mearvk.jdesk.launcher;

import java.io.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * LibraryLinker — Co-Linking Engine for shared library runnables.
 *
 * Architecture:
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  JDesk Desktop                                                   │
 *   │  (User double-clicks .so/.dll icon or manifest references lib)   │
 *   └────────────────────────────┬─────────────────────────────────────┘
 *                                │
 *                                ▼
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  LibraryLinker                                                    │
 *   │  ┌────────────────────────────────────────────────────────────┐  │
 *   │  │ 1. FORMAT DETECTION                                        │  │
 *   │  │    .so  → ELF shared object (ET_DYN, has SONAME)          │  │
 *   │  │    .dll → PE DLL (IMAGE_FILE_DLL flag set)                 │  │
 *   │  │    .dylib → Mach-O MH_DYLIB                               │  │
 *   │  └────────────────────────────────────────────────────────────┘  │
 *   │  ┌────────────────────────────────────────────────────────────┐  │
 *   │  │ 2. SYMBOL INSPECTION                                       │  │
 *   │  │    Enumerate exported symbols via:                         │  │
 *   │  │      ELF: .dynsym section (readelf -Ws equivalent)        │  │
 *   │  │      PE:  Export directory table                           │  │
 *   │  │    Find candidate entry points (priority order):           │  │
 *   │  │      main, plugin_init, service_main, DllMain,            │  │
 *   │  │      jdesk_start, _init, module_init                      │  │
 *   │  └────────────────────────────────────────────────────────────┘  │
 *   │  ┌────────────────────────────────────────────────────────────┐  │
 *   │  │ 3. DEPENDENCY RESOLUTION (Cross-Reference Assumptions)     │  │
 *   │  │    Read DT_NEEDED (ELF) / Import Table (PE)                │  │
 *   │  │    Check if deps available locally or need co-linking      │  │
 *   │  │    Map .dll deps → .so equivalents and vice versa          │  │
 *   │  └────────────────────────────────────────────────────────────┘  │
 *   │  ┌────────────────────────────────────────────────────────────┐  │
 *   │  │ 4. EXECUTION MODE SELECTION                                │  │
 *   │  │    IN_PROCESS: JNI dlopen (trusted, JDesk-native libs)     │  │
 *   │  │    HOST_PROCESS: Sandboxed host (untrusted / services)     │  │
 *   │  │    WINE_HOST: Wine process for .dll execution              │  │
 *   │  │    DARLING_HOST: Darling for .dylib execution              │  │
 *   │  └────────────────────────────────────────────────────────────┘  │
 *   │  ┌────────────────────────────────────────────────────────────┐  │
 *   │  │ 5. LAUNCH (under JVM Memory Proxy governance)              │  │
 *   │  └────────────────────────────────────────────────────────────┘  │
 *   └──────────────────────────────────────────────────────────────────┘
 */
public class LibraryLinker {

    // =========================================================================
    //  Library Format Detection
    // =========================================================================

    /** Supported library formats. */
    public enum LibraryFormat {
        ELF_SHARED_OBJECT(".so", "linux", "ELF Shared Object"),
        PE_DLL(".dll", "windows", "PE Dynamic Link Library"),
        MACHO_DYLIB(".dylib", "macos", "Mach-O Dynamic Library"),
        UNKNOWN(null, null, "Unknown Library Format");

        private final String extension;
        private final String platform;
        private final String description;

        LibraryFormat(String extension, String platform, String description) {
            this.extension = extension;
            this.platform = platform;
            this.description = description;
        }

        public String getExtension() { return extension; }
        public String getPlatform() { return platform; }
        public String getDescription() { return description; }
    }

    /** How the library should be executed. */
    public enum ExecutionMode {
        /** Load in-process via JNI System.load() — trusted, JDesk-native. */
        IN_PROCESS,

        /** Launch in sandboxed host process (jdesk-libhost) — general purpose. */
        HOST_PROCESS,

        /** Load via Wine's LoadLibrary — Windows DLLs. */
        WINE_HOST,

        /** Load via Darling — macOS dylibs. */
        DARLING_HOST
    }

    /** How a library serves: daemon, service, desktop app, or plugin. */
    public enum LibraryRole {
        /** Long-running background daemon (no GUI). */
        DAEMON,

        /** System service (managed lifecycle, may have IPC). */
        SERVICE,

        /** Desktop application (has GUI entry point or hooks into JDesk). */
        DESKTOP_APP,

        /** JDesk plugin (extends desktop functionality in-process). */
        PLUGIN,

        /** Generic library (exported functions, no standalone entry). */
        LIBRARY
    }

    // =========================================================================
    //  Library Descriptor
    // =========================================================================

    /**
     * Complete description of a shared library for execution planning.
     */
    public static class LibraryDescriptor {
        public Path path;
        public LibraryFormat format;
        public ExecutionMode executionMode;
        public LibraryRole role;
        public String soname;                     // ELF SONAME or DLL internal name
        public List<String> exportedSymbols;      // All exported symbol names
        public String entryPoint;                 // Discovered entry point function
        public List<String> dependencies;         // Required libraries (DT_NEEDED / imports)
        public List<String> missingDependencies;  // Deps not found on system
        public Map<String, String> crossLinks;    // .dll→.so mapping or vice versa
        public boolean trusted;                   // In trusted path (/opt/jdesk/libs/)
        public long fileSizeBytes;
        public String architecture;               // x86_64, i386, aarch64, etc.

        @Override
        public String toString() {
            return String.format("LibraryDescriptor{%s, format=%s, entry=%s, role=%s, mode=%s, deps=%d}",
                    path.getFileName(), format.getDescription(), entryPoint, role, executionMode,
                    dependencies != null ? dependencies.size() : 0);
        }
    }

    // =========================================================================
    //  Known Entry Points (Priority-Ordered)
    // =========================================================================

    /**
     * Candidate entry points for treating a library as a runnable.
     * Checked in priority order — first match wins.
     */
    private static final List<EntryPointCandidate> ENTRY_POINT_CANDIDATES = List.of(
        // JDesk-specific entry points (purpose-built for this desktop)
        new EntryPointCandidate("jdesk_start",        LibraryRole.PLUGIN,      "JDesk plugin entry"),
        new EntryPointCandidate("jdesk_service_main", LibraryRole.SERVICE,     "JDesk managed service"),
        new EntryPointCandidate("jdesk_daemon_main",  LibraryRole.DAEMON,      "JDesk background daemon"),
        new EntryPointCandidate("jdesk_app_main",     LibraryRole.DESKTOP_APP, "JDesk desktop application"),

        // Standard Unix/Linux entry points
        new EntryPointCandidate("main",               LibraryRole.DESKTOP_APP, "Standard C entry (linked as -pie)"),
        new EntryPointCandidate("plugin_init",        LibraryRole.PLUGIN,      "Generic plugin initializer"),
        new EntryPointCandidate("module_init",        LibraryRole.PLUGIN,      "Kernel-style module init"),
        new EntryPointCandidate("service_main",       LibraryRole.SERVICE,     "Unix service entry"),
        new EntryPointCandidate("daemon_main",        LibraryRole.DAEMON,      "Daemon entry point"),
        new EntryPointCandidate("_start",             LibraryRole.DESKTOP_APP, "ELF _start (PIE binary)"),

        // Windows DLL entry points
        new EntryPointCandidate("DllMain",            LibraryRole.LIBRARY,     "Windows DLL entry"),
        new EntryPointCandidate("ServiceMain",        LibraryRole.SERVICE,     "Windows service entry"),
        new EntryPointCandidate("WinMain",            LibraryRole.DESKTOP_APP, "Windows GUI app entry"),
        new EntryPointCandidate("wWinMain",           LibraryRole.DESKTOP_APP, "Windows wide-char GUI entry"),
        new EntryPointCandidate("DllRegisterServer",  LibraryRole.SERVICE,     "COM server registration"),

        // macOS entry points
        new EntryPointCandidate("NSApplicationMain",  LibraryRole.DESKTOP_APP, "Cocoa app entry"),
        new EntryPointCandidate("_dyld_start",        LibraryRole.LIBRARY,     "dyld loader entry")
    );

    private static class EntryPointCandidate {
        final String symbolName;
        final LibraryRole impliedRole;
        final String description;

        EntryPointCandidate(String symbolName, LibraryRole impliedRole, String description) {
            this.symbolName = symbolName;
            this.impliedRole = impliedRole;
            this.description = description;
        }
    }

    // =========================================================================
    //  Cross-Reference Assumption Table
    // =========================================================================

    /**
     * Maps common Windows DLL names to their Linux .so equivalents.
     * Used to resolve dependencies when running .dll files on Linux.
     */
    private static final Map<String, String> DLL_TO_SO = Map.ofEntries(
        // C/C++ runtime
        Map.entry("msvcrt.dll",       "libc.so.6"),
        Map.entry("msvcp140.dll",     "libstdc++.so.6"),
        Map.entry("vcruntime140.dll", "libgcc_s.so.1"),
        Map.entry("ucrtbase.dll",     "libc.so.6"),

        // Windows API → Linux equivalents
        Map.entry("kernel32.dll",     "libc.so.6"),
        Map.entry("user32.dll",       "libX11.so.6"),
        Map.entry("gdi32.dll",        "libcairo.so.2"),
        Map.entry("advapi32.dll",     "libpam.so.0"),
        Map.entry("shell32.dll",      "libgio-2.0.so.0"),
        Map.entry("ole32.dll",        "libdbus-1.so.3"),
        Map.entry("ws2_32.dll",       "libc.so.6"),
        Map.entry("winmm.dll",        "libasound.so.2"),
        Map.entry("opengl32.dll",     "libGL.so.1"),
        Map.entry("d3d11.dll",        "libvulkan.so.1"),
        Map.entry("dxgi.dll",         "libvulkan.so.1"),

        // Common application libraries
        Map.entry("libcurl.dll",      "libcurl.so.4"),
        Map.entry("libssl-3.dll",     "libssl.so.3"),
        Map.entry("libcrypto-3.dll",  "libcrypto.so.3"),
        Map.entry("zlib1.dll",        "libz.so.1"),
        Map.entry("libpng16.dll",     "libpng16.so.16"),
        Map.entry("libjpeg-62.dll",   "libjpeg.so.62"),
        Map.entry("SDL2.dll",         "libSDL2-2.0.so.0"),
        Map.entry("sqlite3.dll",      "libsqlite3.so.0"),
        Map.entry("libxml2.dll",      "libxml2.so.2"),
        Map.entry("pthread.dll",      "libpthread.so.0")
    );

    /**
     * Maps Linux .so names to Windows DLL equivalents.
     * Used when a Linux .so has dependencies that might be satisfied by Wine DLLs.
     */
    private static final Map<String, String> SO_TO_DLL = new HashMap<>();
    static {
        // Reverse the DLL→SO map
        for (Map.Entry<String, String> entry : DLL_TO_SO.entrySet()) {
            SO_TO_DLL.putIfAbsent(entry.getValue(), entry.getKey());
        }
    }

    // =========================================================================
    //  Trusted Library Paths
    // =========================================================================

    /** Libraries under these paths are considered trusted (can run in-process). */
    private static final List<String> TRUSTED_LIB_ROOTS = List.of(
        "/opt/jdesk/libs/",
        "/opt/jdesk/plugins/",
        "/usr/lib/jdesk/",
        "/usr/local/lib/jdesk/"
    );

    /** System library search paths for dependency resolution. */
    private static final List<String> SYSTEM_LIB_PATHS = List.of(
        "/lib/x86_64-linux-gnu/",
        "/usr/lib/x86_64-linux-gnu/",
        "/usr/lib/",
        "/usr/local/lib/",
        "/opt/jdesk/libs/",
        "/opt/jdesk/apps/lib/"
    );

    // =========================================================================
    //  Primary API: Inspect and Load
    // =========================================================================

    /**
     * Inspect a shared library file: detect format, enumerate symbols,
     * discover entry points, resolve dependencies, determine execution mode.
     *
     * @param libraryPath Path to .so, .dll, or .dylib file
     * @return Complete library descriptor for execution planning
     */
    public static LibraryDescriptor inspect(Path libraryPath) throws IOException {
        if (!Files.exists(libraryPath)) {
            throw new FileNotFoundException("Library not found: " + libraryPath);
        }

        LibraryDescriptor desc = new LibraryDescriptor();
        desc.path = libraryPath.toAbsolutePath().normalize();
        desc.fileSizeBytes = Files.size(libraryPath);
        desc.exportedSymbols = new ArrayList<>();
        desc.dependencies = new ArrayList<>();
        desc.missingDependencies = new ArrayList<>();
        desc.crossLinks = new LinkedHashMap<>();

        // 1. Detect format from magic bytes and extension
        desc.format = detectLibraryFormat(libraryPath);

        // 2. Extract symbols and dependencies based on format
        switch (desc.format) {
            case ELF_SHARED_OBJECT:
                inspectELF(libraryPath, desc);
                break;
            case PE_DLL:
                inspectPE(libraryPath, desc);
                break;
            case MACHO_DYLIB:
                inspectMachO(libraryPath, desc);
                break;
            default:
                break;
        }

        // 3. Discover entry point
        discoverEntryPoint(desc);

        // 4. Resolve dependencies and build cross-reference
        resolveDependencies(desc);

        // 5. Determine trust level
        desc.trusted = isTrusted(desc.path);

        // 6. Select execution mode
        desc.executionMode = selectExecutionMode(desc);

        return desc;
    }

    /**
     * Load and execute a shared library as a runnable program.
     * Launches under JVM Memory Proxy governance.
     *
     * @param desc Library descriptor (from inspect())
     * @param profile Memory Proxy resource profile name
     * @param args Arguments passed to the library entry point
     * @return Process handle (for HOST_PROCESS, WINE_HOST, DARLING_HOST modes)
     *         or null for IN_PROCESS mode (library loaded into JVM)
     */
    public static Process load(LibraryDescriptor desc, String profile, String[] args) throws IOException {
        System.out.printf("[JDesk:LibLinker] Loading: %s%n", desc.path.getFileName());
        System.out.printf("[JDesk:LibLinker]   Format:     %s%n", desc.format.getDescription());
        System.out.printf("[JDesk:LibLinker]   Entry:      %s%n", desc.entryPoint);
        System.out.printf("[JDesk:LibLinker]   Role:       %s%n", desc.role);
        System.out.printf("[JDesk:LibLinker]   Mode:       %s%n", desc.executionMode);
        System.out.printf("[JDesk:LibLinker]   Trusted:    %s%n", desc.trusted);
        System.out.printf("[JDesk:LibLinker]   Deps:       %d (%d missing)%n",
                desc.dependencies.size(), desc.missingDependencies.size());

        if (!desc.missingDependencies.isEmpty()) {
            System.out.printf("[JDesk:LibLinker]   ⚠ Missing:  %s%n", desc.missingDependencies);
            // Attempt cross-link resolution
            if (!desc.crossLinks.isEmpty()) {
                System.out.printf("[JDesk:LibLinker]   Cross-links available: %s%n", desc.crossLinks);
            }
        }

        switch (desc.executionMode) {
            case IN_PROCESS:
                loadInProcess(desc);
                return null;

            case HOST_PROCESS:
                return launchHostProcess(desc, profile, args);

            case WINE_HOST:
                return launchWineHost(desc, profile, args);

            case DARLING_HOST:
                return launchDarlingHost(desc, profile, args);

            default:
                throw new IOException("Unsupported execution mode: " + desc.executionMode);
        }
    }

    /**
     * Convenience method: inspect + load in one call.
     */
    public static Process loadLibrary(Path libraryPath, String profile, String[] args) throws IOException {
        LibraryDescriptor desc = inspect(libraryPath);
        return load(desc, profile, args);
    }

    // =========================================================================
    //  Format Detection
    // =========================================================================

    private static LibraryFormat detectLibraryFormat(Path path) throws IOException {
        byte[] header = new byte[64];
        int bytesRead;
        try (InputStream is = Files.newInputStream(path)) {
            bytesRead = is.read(header);
        }

        if (bytesRead < 4) return LibraryFormat.UNKNOWN;

        // ELF: \x7fELF + check ET_DYN (shared object)
        if (header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
            // ELF type is at offset 16 (e_type), 2 bytes, little-endian on x86_64
            if (bytesRead >= 18) {
                int eType = (header[16] & 0xFF) | ((header[17] & 0xFF) << 8);
                // ET_DYN = 3 (shared object), ET_EXEC = 2 (executable)
                // Modern PIE executables are also ET_DYN, but .so files have SONAME
                if (eType == 3) return LibraryFormat.ELF_SHARED_OBJECT;
            }
            // Even if ET_EXEC, if filename ends in .so, treat as shared obj
            if (path.toString().contains(".so")) return LibraryFormat.ELF_SHARED_OBJECT;
        }

        // PE: MZ header → check DLL flag
        if (header[0] == 'M' && header[1] == 'Z') {
            // PE offset is at 0x3C (4 bytes, LE)
            if (bytesRead >= 64) {
                int peOffset = ByteBuffer.wrap(header, 0x3C, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                // We'd need to read further to check Characteristics for IMAGE_FILE_DLL (0x2000)
                // For now, trust the .dll extension
                if (path.toString().toLowerCase().endsWith(".dll")) return LibraryFormat.PE_DLL;
            }
            // Fallback: extension-based
            if (path.toString().toLowerCase().endsWith(".dll")) return LibraryFormat.PE_DLL;
        }

        // Mach-O: check for MH_DYLIB (filetype = 6)
        if (bytesRead >= 16) {
            int magic = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (magic == 0xFEEDFACF || magic == 0xFEEDFACE) {
                // filetype at offset 12 (4 bytes)
                int filetype = ByteBuffer.wrap(header, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (filetype == 6) return LibraryFormat.MACHO_DYLIB; // MH_DYLIB
            }
        }

        // Extension-based fallback
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".so") || name.contains(".so.")) return LibraryFormat.ELF_SHARED_OBJECT;
        if (name.endsWith(".dll")) return LibraryFormat.PE_DLL;
        if (name.endsWith(".dylib")) return LibraryFormat.MACHO_DYLIB;

        return LibraryFormat.UNKNOWN;
    }

    // =========================================================================
    //  ELF Inspection (via external readelf or libelf JNI)
    // =========================================================================

    /**
     * Extract exported symbols and dependencies from an ELF shared object.
     * Uses 'readelf' CLI (available on all Linux systems via binutils).
     *
     * For production, this could use libelf via JNI for zero-fork inspection.
     */
    private static void inspectELF(Path path, LibraryDescriptor desc) throws IOException {
        desc.architecture = detectELFArch(path);

        // Extract dynamic symbols (exported functions)
        ProcessBuilder pbSyms = new ProcessBuilder(
            "readelf", "--dyn-syms", "--wide", path.toString());
        pbSyms.redirectErrorStream(true);
        Process proc = pbSyms.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            Pattern symPattern = Pattern.compile(
                "\\s+\\d+:\\s+[0-9a-f]+\\s+\\d+\\s+\\w+\\s+GLOBAL\\s+DEFAULT\\s+\\d+\\s+(\\S+)");
            while ((line = reader.readLine()) != null) {
                java.util.regex.Matcher m = symPattern.matcher(line);
                if (m.find()) {
                    String symbol = m.group(1);
                    if (!symbol.isEmpty() && !symbol.startsWith("_DYNAMIC")) {
                        desc.exportedSymbols.add(symbol);
                    }
                }
            }
        }

        // Extract dependencies (DT_NEEDED entries)
        ProcessBuilder pbDeps = new ProcessBuilder(
            "readelf", "--dynamic", "--wide", path.toString());
        pbDeps.redirectErrorStream(true);
        Process procDeps = pbDeps.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(procDeps.getInputStream()))) {
            String line;
            Pattern neededPattern = Pattern.compile("\\(NEEDED\\)\\s+Shared library:\\s+\\[(.+?)\\]");
            Pattern sonamePattern = Pattern.compile("\\(SONAME\\)\\s+Library soname:\\s+\\[(.+?)\\]");
            while ((line = reader.readLine()) != null) {
                java.util.regex.Matcher mNeeded = neededPattern.matcher(line);
                if (mNeeded.find()) {
                    desc.dependencies.add(mNeeded.group(1));
                }
                java.util.regex.Matcher mSoname = sonamePattern.matcher(line);
                if (mSoname.find()) {
                    desc.soname = mSoname.group(1);
                }
            }
        }

        try { proc.waitFor(); } catch (InterruptedException ignored) {}
        try { procDeps.waitFor(); } catch (InterruptedException ignored) {}
    }

    private static String detectELFArch(Path path) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("readelf", "-h", path.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Machine:")) {
                    if (line.contains("X86-64") || line.contains("Advanced Micro Devices X86-64"))
                        return "x86_64";
                    if (line.contains("Intel 80386")) return "i386";
                    if (line.contains("AArch64")) return "aarch64";
                    if (line.contains("ARM")) return "arm";
                    return line.substring(line.indexOf("Machine:") + 8).trim();
                }
            }
        }

        try { proc.waitFor(); } catch (InterruptedException ignored) {}
        return "unknown";
    }

    // =========================================================================
    //  PE DLL Inspection (via external tools or pe-parse JNI)
    // =========================================================================

    /**
     * Extract exported symbols and dependencies from a PE DLL.
     * Uses 'objdump' (from binutils, handles PE format on Linux)
     * or 'winedump' if available.
     *
     * Open-source alternatives:
     *   - pe-parse (Trail of Bits, MIT): github.com/trailofbits/pe-parse
     *   - pefile (Python, MIT): for scripted analysis
     *   - objdump -p (GNU binutils): already installed on all Linux
     */
    private static void inspectPE(Path path, LibraryDescriptor desc) throws IOException {
        desc.architecture = "x86_64"; // Assume; refine with PE header parsing

        // Try objdump for PE export table
        ProcessBuilder pbExports = new ProcessBuilder(
            "objdump", "-p", path.toString());
        pbExports.redirectErrorStream(true);
        Process proc = pbExports.start();

        boolean inExportSection = false;
        boolean inImportSection = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Export table section
                if (line.contains("Export Table")) {
                    inExportSection = true;
                    inImportSection = false;
                    continue;
                }
                if (line.contains("Import Table") || line.contains("DLL Name:")) {
                    inExportSection = false;
                    inImportSection = true;
                }

                // Parse exported function names
                if (inExportSection) {
                    // Lines like: "         12345  0x01234  FunctionName"
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("[") && !trimmed.contains("Ordinal")) {
                        String[] parts = trimmed.split("\\s+");
                        if (parts.length >= 3) {
                            String symbol = parts[parts.length - 1];
                            if (symbol.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                                desc.exportedSymbols.add(symbol);
                            }
                        }
                    }
                }

                // Parse DLL dependencies
                if (inImportSection && line.contains("DLL Name:")) {
                    String dllName = line.substring(line.indexOf("DLL Name:") + 9).trim();
                    if (!dllName.isEmpty()) {
                        desc.dependencies.add(dllName);
                    }
                }
            }
        }

        try { proc.waitFor(); } catch (InterruptedException ignored) {}

        // PE internal name from export table
        if (desc.soname == null) {
            desc.soname = path.getFileName().toString();
        }
    }

    // =========================================================================
    //  Mach-O Inspection
    // =========================================================================

    private static void inspectMachO(Path path, LibraryDescriptor desc) throws IOException {
        desc.architecture = "x86_64"; // Assume for now

        // Use 'nm' for symbol export if Darling tools available, else objdump
        ProcessBuilder pb = new ProcessBuilder("nm", "-gU", path.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3) {
                    String symbol = parts[2];
                    if (symbol.startsWith("_")) symbol = symbol.substring(1);
                    desc.exportedSymbols.add(symbol);
                }
            }
        }

        try { proc.waitFor(); } catch (InterruptedException ignored) {}
    }

    // =========================================================================
    //  Entry Point Discovery
    // =========================================================================

    /**
     * Discover the best entry point from exported symbols.
     * Priority-ordered: JDesk-specific → standard → platform-specific.
     */
    private static void discoverEntryPoint(LibraryDescriptor desc) {
        if (desc.exportedSymbols == null || desc.exportedSymbols.isEmpty()) {
            desc.entryPoint = null;
            desc.role = LibraryRole.LIBRARY;
            return;
        }

        Set<String> symbolSet = new HashSet<>(desc.exportedSymbols);

        // Check candidates in priority order
        for (EntryPointCandidate candidate : ENTRY_POINT_CANDIDATES) {
            if (symbolSet.contains(candidate.symbolName)) {
                desc.entryPoint = candidate.symbolName;
                desc.role = candidate.impliedRole;
                System.out.printf("[JDesk:LibLinker] Entry point found: %s (%s)%n",
                        candidate.symbolName, candidate.description);
                return;
            }
        }

        // No standard entry point found — check for patterns
        for (String sym : desc.exportedSymbols) {
            if (sym.endsWith("_main") || sym.endsWith("_init") || sym.endsWith("_start")) {
                desc.entryPoint = sym;
                desc.role = LibraryRole.PLUGIN;
                System.out.printf("[JDesk:LibLinker] Inferred entry: %s (pattern match)%n", sym);
                return;
            }
        }

        // No entry point — this is a pure library
        desc.entryPoint = null;
        desc.role = LibraryRole.LIBRARY;
    }

    // =========================================================================
    //  Dependency Resolution & Cross-Referencing
    // =========================================================================

    /**
     * Resolve dependencies: check which are available on the system,
     * build cross-reference mappings between .dll and .so names.
     */
    private static void resolveDependencies(LibraryDescriptor desc) {
        if (desc.dependencies == null) return;

        for (String dep : desc.dependencies) {
            boolean found = false;
            String depLower = dep.toLowerCase();

            // Check if the dependency is available in system library paths
            for (String libPath : SYSTEM_LIB_PATHS) {
                Path candidate = Path.of(libPath, dep);
                if (Files.exists(candidate)) {
                    found = true;
                    break;
                }
                // Also check versioned names (libfoo.so.1, libfoo.so.1.2.3)
                try (var stream = Files.newDirectoryStream(Path.of(libPath), dep + "*")) {
                    if (stream.iterator().hasNext()) {
                        found = true;
                        break;
                    }
                } catch (IOException ignored) {}
            }

            // If not found natively, try cross-reference
            if (!found) {
                String crossLink = null;
                if (desc.format == LibraryFormat.PE_DLL) {
                    // Windows DLL dep → find Linux .so equivalent
                    crossLink = DLL_TO_SO.get(depLower);
                    if (crossLink == null) {
                        // Pattern match: libfoo.dll → libfoo.so
                        crossLink = depLower.replace(".dll", ".so");
                    }
                } else if (desc.format == LibraryFormat.ELF_SHARED_OBJECT) {
                    // Linux .so dep → find Windows equivalent (for co-linking reference)
                    crossLink = SO_TO_DLL.get(dep);
                }

                if (crossLink != null) {
                    desc.crossLinks.put(dep, crossLink);
                    // Check if the cross-linked library exists
                    boolean crossFound = false;
                    for (String libPath : SYSTEM_LIB_PATHS) {
                        if (Files.exists(Path.of(libPath, crossLink))) {
                            crossFound = true;
                            break;
                        }
                    }
                    if (!crossFound) {
                        desc.missingDependencies.add(dep + " (cross-ref: " + crossLink + ")");
                    }
                } else {
                    desc.missingDependencies.add(dep);
                }
            }
        }
    }

    // =========================================================================
    //  Execution Mode Selection
    // =========================================================================

    /**
     * Determine how to execute this library based on format, trust, and role.
     */
    private static ExecutionMode selectExecutionMode(LibraryDescriptor desc) {
        // Windows DLLs always go through Wine
        if (desc.format == LibraryFormat.PE_DLL) {
            return ExecutionMode.WINE_HOST;
        }

        // macOS dylibs always go through Darling
        if (desc.format == LibraryFormat.MACHO_DYLIB) {
            return ExecutionMode.DARLING_HOST;
        }

        // ELF shared objects: trusted plugins load in-process, everything else is hosted
        if (desc.format == LibraryFormat.ELF_SHARED_OBJECT) {
            if (desc.trusted && desc.role == LibraryRole.PLUGIN) {
                return ExecutionMode.IN_PROCESS;
            }
            return ExecutionMode.HOST_PROCESS;
        }

        return ExecutionMode.HOST_PROCESS;
    }

    private static boolean isTrusted(Path path) {
        String pathStr = path.toString();
        for (String root : TRUSTED_LIB_ROOTS) {
            if (pathStr.startsWith(root)) return true;
        }
        return false;
    }

    // =========================================================================
    //  Execution: In-Process (JNI dlopen)
    // =========================================================================

    /**
     * Load a trusted library directly into the JVM process via System.load().
     * Only for PLUGIN role libraries in trusted paths.
     */
    private static void loadInProcess(LibraryDescriptor desc) throws IOException {
        if (!desc.trusted) {
            throw new SecurityException("Cannot load untrusted library in-process: " + desc.path);
        }

        System.out.printf("[JDesk:LibLinker] Loading in-process (JNI): %s%n", desc.path);

        try {
            System.load(desc.path.toString());
            System.out.printf("[JDesk:LibLinker] ✓ Loaded successfully: %s%n", desc.path.getFileName());

            // If it has a JDesk entry point, invoke via JNI
            // The native library should register its JNI methods in JNI_OnLoad
        } catch (UnsatisfiedLinkError e) {
            throw new IOException("Failed to load library: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    //  Execution: Host Process (jdesk-libhost)
    // =========================================================================

    /**
     * Launch a shared library in the jdesk-libhost sandboxed process.
     *
     * jdesk-libhost is a minimal C program that:
     *   1. Opens the .so via dlopen()
     *   2. Resolves the entry point via dlsym()
     *   3. Calls the entry function with provided arguments
     *   4. Monitors for crashes (signal handlers)
     *   5. Provides IPC back to JDesk (Unix socket)
     *
     * The whole thing runs under java -memory-guard for resource governance.
     */
    private static Process launchHostProcess(LibraryDescriptor desc, String profile, String[] args)
            throws IOException {
        List<String> cmd = new ArrayList<>();

        // JVM Memory Proxy wrapper
        cmd.add("java");
        cmd.add("-memory-guard");
        cmd.add("-Xguard:profile=" + sanitizeProfile(profile));
        cmd.add("-Xguard:status=5s");

        // The host process binary
        cmd.add("/opt/jdesk/bin/jdesk-libhost");

        // Library to load
        cmd.add("--library");
        cmd.add(desc.path.toString());

        // Entry point to invoke
        if (desc.entryPoint != null) {
            cmd.add("--entry");
            cmd.add(desc.entryPoint);
        }

        // Role hint (affects lifecycle management)
        cmd.add("--role");
        cmd.add(desc.role.name().toLowerCase());

        // Additional library search paths for dependencies
        cmd.add("--lib-path");
        cmd.add(String.join(":", SYSTEM_LIB_PATHS));

        // Cross-link overrides
        for (Map.Entry<String, String> link : desc.crossLinks.entrySet()) {
            cmd.add("--cross-link");
            cmd.add(link.getKey() + "=" + link.getValue());
        }

        // Arguments to pass to the library entry point
        if (args != null) {
            cmd.add("--");
            for (String arg : args) {
                if (arg != null && !arg.isBlank()) cmd.add(arg);
            }
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        pb.environment().put("JDESK_LIB_NAME", desc.soname != null ? desc.soname : desc.path.getFileName().toString());
        pb.environment().put("JDESK_LIB_ROLE", desc.role.name());

        return pb.start();
    }

    // =========================================================================
    //  Execution: Wine Host (Windows DLLs)
    // =========================================================================

    /**
     * Launch a Windows DLL via Wine's rundll32 or a custom Wine host process.
     *
     * For DLLs with standard entry points:
     *   wine rundll32.exe library.dll,EntryPoint [args]
     *
     * For DLLs with ServiceMain or WinMain:
     *   wine jdesk-dllhost.exe library.dll [args]
     */
    private static Process launchWineHost(LibraryDescriptor desc, String profile, String[] args)
            throws IOException {
        List<String> cmd = new ArrayList<>();

        // JVM Memory Proxy wrapper
        cmd.add("java");
        cmd.add("-memory-guard");
        cmd.add("-Xguard:profile=" + sanitizeProfile(profile));
        cmd.add("-Xguard:status=5s");

        // Wine execution
        cmd.add("wine");

        if (desc.role == LibraryRole.DESKTOP_APP || desc.role == LibraryRole.SERVICE) {
            // Use custom DLL host for apps/services
            cmd.add("/opt/jdesk/bin/jdesk-dllhost.exe");
            cmd.add(desc.path.toString());
            if (desc.entryPoint != null) {
                cmd.add(desc.entryPoint);
            }
        } else {
            // Use Windows rundll32 for generic DLL entry points
            cmd.add("rundll32.exe");
            String entrySpec = desc.path.getFileName().toString();
            if (desc.entryPoint != null) {
                entrySpec += "," + desc.entryPoint;
            }
            cmd.add(entrySpec);
        }

        // Arguments
        if (args != null) {
            for (String arg : args) {
                if (arg != null && !arg.isBlank()) cmd.add(arg);
            }
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        pb.environment().put("WINEPREFIX", "/opt/jdesk/wine-prefix");
        pb.environment().put("WINEDEBUG", "-all");  // Quiet by default

        return pb.start();
    }

    // =========================================================================
    //  Execution: Darling Host (macOS dylibs)
    // =========================================================================

    private static Process launchDarlingHost(LibraryDescriptor desc, String profile, String[] args)
            throws IOException {
        List<String> cmd = new ArrayList<>();

        cmd.add("java");
        cmd.add("-memory-guard");
        cmd.add("-Xguard:profile=" + sanitizeProfile(profile));
        cmd.add("-Xguard:status=5s");

        cmd.add("darling");
        cmd.add("shell");
        cmd.add("/opt/jdesk/bin/jdesk-dylibhost");
        cmd.add(desc.path.toString());
        if (desc.entryPoint != null) {
            cmd.add(desc.entryPoint);
        }

        if (args != null) {
            for (String arg : args) {
                if (arg != null && !arg.isBlank()) cmd.add(arg);
            }
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();

        return pb.start();
    }

    // =========================================================================
    //  Utility
    // =========================================================================

    private static final Pattern SAFE_PROFILE_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]{1,64}$");

    private static String sanitizeProfile(String profile) {
        if (profile == null || !SAFE_PROFILE_PATTERN.matcher(profile).matches()) {
            return "default";
        }
        return profile;
    }

    // =========================================================================
    //  CLI Main (for testing and standalone use)
    // =========================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("""
                JDesk Library Linker — Co-Linking Engine for .so / .dll / .dylib

                Usage:
                  LibraryLinker --inspect <library>          Inspect library (symbols, deps, entry)
                  LibraryLinker --load <library> [args...]   Load and execute library
                  LibraryLinker --deps <library>             Show dependency tree
                  LibraryLinker --cross-ref <library>        Show cross-platform reference map
                  LibraryLinker --formats                    Show supported formats

                Examples:
                  LibraryLinker --inspect /opt/jdesk/libs/libwidget.so
                  LibraryLinker --load /opt/jdesk/apps/lib/myservice.so
                  LibraryLinker --load /opt/jdesk/apps/lib/plugin.dll --args startup
                  LibraryLinker --deps /opt/jdesk/apps/lib/complex.dll
                """);
            System.exit(1);
        }

        switch (args[0]) {
            case "--inspect": {
                LibraryDescriptor desc = inspect(Path.of(args[1]));
                printDescriptor(desc);
                break;
            }
            case "--load": {
                String[] loadArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : null;
                Process proc = loadLibrary(Path.of(args[1]), "default", loadArgs);
                if (proc != null) {
                    proc.waitFor();
                    System.out.printf("[JDesk:LibLinker] Process exited: %d%n", proc.exitValue());
                }
                break;
            }
            case "--deps": {
                LibraryDescriptor desc = inspect(Path.of(args[1]));
                System.out.println("Dependencies for: " + desc.path.getFileName());
                System.out.println("─".repeat(60));
                for (String dep : desc.dependencies) {
                    String status = desc.missingDependencies.stream()
                            .anyMatch(m -> m.startsWith(dep)) ? "✗ MISSING" : "✓ found";
                    String cross = desc.crossLinks.getOrDefault(dep, "");
                    if (!cross.isEmpty()) cross = " → " + cross;
                    System.out.printf("  %-30s  %s%s%n", dep, status, cross);
                }
                break;
            }
            case "--cross-ref": {
                LibraryDescriptor desc = inspect(Path.of(args[1]));
                System.out.println("Cross-Platform Reference Map: " + desc.path.getFileName());
                System.out.println("─".repeat(60));
                if (desc.crossLinks.isEmpty()) {
                    System.out.println("  No cross-platform links needed.");
                } else {
                    for (Map.Entry<String, String> entry : desc.crossLinks.entrySet()) {
                        System.out.printf("  %-25s  →  %s%n", entry.getKey(), entry.getValue());
                    }
                }
                break;
            }
            case "--formats": {
                System.out.println("""
                    Supported Library Formats:
                    ─────────────────────────────────────────────────────────────
                      .so      ELF Shared Object (Linux)      → direct dlopen or host
                      .dll     PE Dynamic Link Library (Win)   → Wine host
                      .dylib   Mach-O Dynamic Library (macOS)  → Darling host
                    
                    All formats execute under JVM Memory Proxy governance.
                    
                    Execution Modes:
                      IN_PROCESS    JNI System.load() — trusted JDesk plugins only
                      HOST_PROCESS  jdesk-libhost (sandboxed dlopen + entry call)
                      WINE_HOST     Wine rundll32 or jdesk-dllhost.exe
                      DARLING_HOST  Darling shell + jdesk-dylibhost
                    """);
                break;
            }
            default:
                System.err.println("Unknown command: " + args[0]);
                System.exit(1);
        }
    }

    private static void printDescriptor(LibraryDescriptor desc) {
        System.out.printf("""
            ═══════════════════════════════════════════════════════════════
              JDesk Library Linker — Inspection Report
            ═══════════════════════════════════════════════════════════════
              File:           %s
              Format:         %s
              Architecture:   %s
              Size:           %s
              SONAME:         %s
              Trusted:        %s
            
              Entry Point:    %s
              Role:           %s
              Execution Mode: %s
            
              Exported Symbols: %d
              Dependencies:     %d (%d missing)
              Cross-Links:      %d
            ═══════════════════════════════════════════════════════════════
            %n""",
            desc.path,
            desc.format.getDescription(),
            desc.architecture,
            formatSize(desc.fileSizeBytes),
            desc.soname != null ? desc.soname : "(none)",
            desc.trusted,
            desc.entryPoint != null ? desc.entryPoint : "(none — pure library)",
            desc.role,
            desc.executionMode,
            desc.exportedSymbols.size(),
            desc.dependencies.size(),
            desc.missingDependencies.size(),
            desc.crossLinks.size());

        // Top 20 exported symbols
        if (!desc.exportedSymbols.isEmpty()) {
            System.out.println("  Exported Symbols (first 20):");
            desc.exportedSymbols.stream().limit(20).forEach(s -> System.out.println("    " + s));
            if (desc.exportedSymbols.size() > 20)
                System.out.printf("    ... and %d more%n", desc.exportedSymbols.size() - 20);
        }
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        if (bytes >= 1024L) return String.format("%.1f KB", bytes / 1024.0);
        return bytes + " B";
    }
}
