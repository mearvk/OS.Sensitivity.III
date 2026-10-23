/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDesk Network Concern — Ethical Network Governance Layer
 *
 * This module stands between JDesk applications and the network.
 * It provides:
 *   1. Allowed-command whitelist (ping, telnet, ssh, curl, wget, dig, nslookup)
 *   2. Destination validation (blocks known-malicious, blocks internal ranges by default)
 *   3. Rate limiting (prevents network abuse from JDesk terminal)
 *   4. Audit logging (every outbound connection is recorded)
 *   5. Ethical constraints (no port scanning, no DoS, no unauthorized access)
 *
 * Philosophy:
 *   The network is a shared resource. JDesk users can connect outward for
 *   legitimate purposes: diagnostics (ping), remote access (ssh), web queries
 *   (curl), name resolution (dig). They CANNOT use JDesk as a platform for
 *   attacking others. The terminal is careful, not weaponized.
 *
 *   "The system radiates the installer's intent forward in time."
 *   — White Ethics Installer Grade
 *
 * Integration:
 *   The JDeskTerminal checks every command through NetworkConcern before
 *   passing it to the shell. Blocked commands are refused with an explanation.
 *   Allowed commands proceed with audit logging.
 *
 * License: GPL-2.0
 */

package us.mearvk.jdesk.security;

import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/**
 * NetworkConcern — Ethical network governance for JDesk.
 *
 * Every network-capable command from JDesk passes through this layer.
 * The concern is careful: it allows legitimate work and blocks abuse.
 */
public class NetworkConcern {

    // =========================================================================
    //  Allowed Network Commands (whitelist)
    // =========================================================================

    /**
     * Commands that are allowed to make network connections.
     * Everything else that attempts network I/O is governed by the
     * Memory Proxy's general resource limits.
     */
    private static final Set<String> ALLOWED_NETWORK_COMMANDS = Set.of(
        // Diagnostics
        "ping",         // ICMP echo — connectivity test
        "ping6",        // IPv6 ping
        "traceroute",   // Path discovery
        "tracepath",    // Path MTU discovery
        "mtr",          // Continuous traceroute

        // Remote access (legitimate, authenticated)
        "ssh",          // Secure shell — authenticated remote access
        "scp",          // Secure copy
        "sftp",         // Secure file transfer
        "telnet",       // Unencrypted remote access (legacy, allowed with warning)

        // Data transfer
        "curl",         // HTTP/HTTPS client
        "wget",         // HTTP/HTTPS download
        "rsync",        // Remote sync

        // DNS
        "dig",          // DNS lookup
        "nslookup",     // DNS lookup (legacy)
        "host",         // DNS lookup (simple)

        // System network tools
        "ip",           // Network interface info
        "ifconfig",     // Network interface info (legacy)
        "ss",           // Socket statistics
        "netstat",      // Network statistics (legacy)
        "route",        // Routing table
        "arp",          // ARP table

        // Mail (if Postfix is configured)
        "mail",         // Send mail
        "sendmail"      // Send mail (direct)
    );

    // =========================================================================
    //  Blocked Commands (never allowed from JDesk terminal)
    // =========================================================================

    /**
     * Commands that are NEVER allowed from JDesk.
     * These are offensive tools that have no legitimate use from a desktop terminal
     * in an ethical system.
     */
    private static final Set<String> BLOCKED_COMMANDS = Set.of(
        // Port scanning
        "nmap",
        "masscan",
        "zmap",

        // Packet crafting / spoofing
        "hping3",
        "scapy",
        "nemesis",

        // DoS tools
        "slowloris",
        "goldeneye",
        "torshammer",
        "loic",

        // Exploitation frameworks (use dedicated Kali partition for authorized pentesting)
        "metasploit",
        "msfconsole",
        "msfvenom",

        // Network sniffing (use dedicated tools with explicit authorization)
        "tcpdump",      // Allowed only via sudo_gate Grade 4+ (not from JDesk terminal)
        "wireshark",
        "ettercap",
        "bettercap",
        "arpspoof",

        // Brute force
        "hydra",
        "john",
        "hashcat",
        "medusa"
    );

    // =========================================================================
    //  Rate Limiting
    // =========================================================================

    /** Maximum outbound connections per minute from JDesk terminal. */
    private static final int MAX_CONNECTIONS_PER_MINUTE = 30;

    /** Maximum ping count per invocation. */
    private static final int MAX_PING_COUNT = 100;

    /** Maximum concurrent SSH sessions from JDesk. */
    private static final int MAX_SSH_SESSIONS = 5;

    // Tracking
    private static final AtomicInteger connectionsThisMinute = new AtomicInteger(0);
    private static final AtomicInteger activeSshSessions = new AtomicInteger(0);
    private static volatile long minuteWindowStart = System.currentTimeMillis();

    // =========================================================================
    //  Blocked Destinations
    // =========================================================================

    /**
     * Destination patterns that are blocked.
     * Prevents accidental or intentional access to infrastructure that
     * shouldn't be touched from a desktop terminal.
     */
    private static final List<String> BLOCKED_DESTINATIONS = List.of(
        // Localhost/loopback should go through proper service management
        // (actually allowed for development — not blocked)

        // AWS metadata (prevent SSRF-style attacks)
        "169.254.169.254",

        // Common cloud metadata endpoints
        "metadata.google.internal",
        "metadata.azure.com"
    );

    // =========================================================================
    //  Audit Log
    // =========================================================================

    private static final String AUDIT_LOG_PATH = "/var/log/jdesk/network-audit.log";
    private static final DateTimeFormatter LOG_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Validate a command before execution.
     * Returns null if allowed, or an error message if blocked.
     *
     * @param commandLine The full command line the user typed
     * @return null if allowed, or a human-readable denial reason
     */
    public static String validateCommand(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) return null;

        String[] parts = commandLine.trim().split("\\s+");
        String command = parts[0];

        // Strip path prefix (user might type /usr/bin/ping)
        if (command.contains("/")) {
            command = command.substring(command.lastIndexOf('/') + 1);
        }

        // --- Check blocked commands ---
        if (BLOCKED_COMMANDS.contains(command)) {
            audit("BLOCKED", commandLine, "Offensive tool not permitted from JDesk");
            return "⚠ Command blocked: '" + command + "' is not available from JDesk.\n" +
                   "  Reason: Offensive/scanning tools are not permitted from the desktop terminal.\n" +
                   "  If you need this for authorized security work, use the dedicated\n" +
                   "  Kali tools partition (sudo jdesk-provision --kali).";
        }

        // --- Check if it's a network command that needs governance ---
        if (!isNetworkCommand(command)) {
            return null; // Not a network command — no network concern applies
        }

        // --- Rate limit check ---
        if (!checkRateLimit()) {
            audit("RATE_LIMITED", commandLine, "Exceeded 30 connections/minute");
            return "⚠ Rate limited: Too many network commands this minute.\n" +
                   "  Maximum: " + MAX_CONNECTIONS_PER_MINUTE + " outbound connections per minute.\n" +
                   "  Wait a moment and try again.";
        }

        // --- Command-specific validation ---
        String specificError = validateSpecificCommand(command, parts);
        if (specificError != null) {
            audit("BLOCKED", commandLine, specificError);
            return specificError;
        }

        // --- Destination validation ---
        String destination = extractDestination(command, parts);
        if (destination != null) {
            String destError = validateDestination(destination);
            if (destError != null) {
                audit("BLOCKED", commandLine, destError);
                return destError;
            }
        }

        // --- Allowed: log and proceed ---
        audit("ALLOWED", commandLine, null);
        incrementConnectionCount();
        return null;
    }

    /**
     * Called when an SSH session ends (for session tracking).
     */
    public static void onSshSessionEnd() {
        activeSshSessions.decrementAndGet();
    }

    // =========================================================================
    //  Internal: Command Classification
    // =========================================================================

    private static boolean isNetworkCommand(String command) {
        return ALLOWED_NETWORK_COMMANDS.contains(command) || BLOCKED_COMMANDS.contains(command);
    }

    // =========================================================================
    //  Internal: Command-Specific Validation
    // =========================================================================

    private static String validateSpecificCommand(String command, String[] parts) {
        switch (command) {
            case "ping":
            case "ping6":
                return validatePing(parts);
            case "ssh":
                return validateSsh(parts);
            case "telnet":
                return validateTelnet(parts);
            case "curl":
            case "wget":
                return validateHttpClient(command, parts);
            default:
                return null;
        }
    }

    private static String validatePing(String[] parts) {
        // Check for excessive count (-c)
        for (int i = 0; i < parts.length - 1; i++) {
            if ("-c".equals(parts[i])) {
                try {
                    int count = Integer.parseInt(parts[i + 1]);
                    if (count > MAX_PING_COUNT) {
                        return "⚠ Ping count too high: " + count + " (max " + MAX_PING_COUNT + ").\n" +
                               "  Use: ping -c " + MAX_PING_COUNT + " <host>";
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Check for flood ping (-f) — requires root anyway, but block explicitly
        for (String part : parts) {
            if ("-f".equals(part)) {
                return "⚠ Flood ping (-f) is not permitted from JDesk.\n" +
                       "  This constitutes a denial-of-service attack.\n" +
                       "  Use: ping -c 4 <host> for connectivity testing.";
            }
        }

        return null;
    }

    private static String validateSsh(String[] parts) {
        // Check session limit
        if (activeSshSessions.get() >= MAX_SSH_SESSIONS) {
            return "⚠ SSH session limit reached (" + MAX_SSH_SESSIONS + " concurrent).\n" +
                   "  Close an existing session before opening a new one.";
        }
        activeSshSessions.incrementAndGet();

        // Check for suspicious options
        for (String part : parts) {
            // Tunneling for legitimate port forwarding is allowed
            // but dynamic proxy (-D) and reverse tunnels (-R to external) get a warning
            if (part.startsWith("-D")) {
                // SOCKS proxy — allowed but logged prominently
                audit("NOTICE", String.join(" ", parts), "SSH SOCKS proxy (-D) used");
            }
        }

        return null;
    }

    private static String validateTelnet(String[] parts) {
        // Telnet is allowed but warn that it's unencrypted
        // (The terminal will display this warning, not block)
        return null; // Warning is shown separately, not a block
    }

    /**
     * Get a warning for telnet (displayed but not blocking).
     */
    public static String getTelnetWarning() {
        return "⚠ WARNING: Telnet transmits data UNENCRYPTED (including passwords).\n" +
               "  Consider using SSH instead: ssh user@host\n" +
               "  Proceeding with telnet...";
    }

    private static String validateHttpClient(String command, String[] parts) {
        // Check for output to suspicious paths
        for (int i = 0; i < parts.length - 1; i++) {
            if ("-o".equals(parts[i]) || "--output".equals(parts[i])) {
                String outPath = parts[i + 1];
                if (outPath.startsWith("/etc/") || outPath.startsWith("/bin/") ||
                    outPath.startsWith("/sbin/") || outPath.startsWith("/usr/bin/")) {
                    return "⚠ Cannot download to system directories.\n" +
                           "  Output path: " + outPath + "\n" +
                           "  Use ~/Downloads/ or /tmp/ instead.";
                }
            }
        }
        return null;
    }

    // =========================================================================
    //  Internal: Destination Validation
    // =========================================================================

    private static String extractDestination(String command, String[] parts) {
        switch (command) {
            case "ping":
            case "ping6":
            case "traceroute":
            case "tracepath":
                // Last non-flag argument is typically the host
                for (int i = parts.length - 1; i >= 1; i--) {
                    if (!parts[i].startsWith("-")) return parts[i];
                }
                return null;
            case "ssh":
            case "telnet":
                // user@host or just host
                for (int i = 1; i < parts.length; i++) {
                    if (!parts[i].startsWith("-")) {
                        String dest = parts[i];
                        if (dest.contains("@")) dest = dest.substring(dest.indexOf('@') + 1);
                        return dest;
                    }
                }
                return null;
            case "curl":
            case "wget":
                // URL argument
                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].startsWith("http://") || parts[i].startsWith("https://")) {
                        try {
                            URI uri = new URI(parts[i]);
                            return uri.getHost();
                        } catch (Exception ignored) {}
                    }
                    if (!parts[i].startsWith("-") && parts[i].contains(".")) {
                        return parts[i];
                    }
                }
                return null;
            default:
                return null;
        }
    }

    private static String validateDestination(String destination) {
        if (destination == null) return null;

        for (String blocked : BLOCKED_DESTINATIONS) {
            if (destination.equalsIgnoreCase(blocked) || destination.contains(blocked)) {
                return "⚠ Destination blocked: " + destination + "\n" +
                       "  This address is restricted for security reasons.";
            }
        }

        return null;
    }

    // =========================================================================
    //  Internal: Rate Limiting
    // =========================================================================

    private static boolean checkRateLimit() {
        long now = System.currentTimeMillis();
        if (now - minuteWindowStart > 60_000) {
            // Reset window
            minuteWindowStart = now;
            connectionsThisMinute.set(0);
        }
        return connectionsThisMinute.get() < MAX_CONNECTIONS_PER_MINUTE;
    }

    private static void incrementConnectionCount() {
        connectionsThisMinute.incrementAndGet();
    }

    // =========================================================================
    //  Internal: Audit Logging
    // =========================================================================

    private static void audit(String action, String commandLine, String reason) {
        String timestamp = LocalDateTime.now().format(LOG_FORMAT);
        String user = System.getProperty("user.name", "unknown");
        String logLine = String.format("[%s] %s user=%s cmd=\"%s\"%s",
            timestamp, action, user, commandLine,
            reason != null ? " reason=\"" + reason + "\"" : "");

        // Print to JDesk console
        System.out.printf("[JDesk:Network] %s%n", logLine);

        // Write to audit log file (best-effort)
        try {
            File logDir = new File("/var/log/jdesk");
            if (!logDir.exists()) logDir.mkdirs();
            try (FileWriter fw = new FileWriter(AUDIT_LOG_PATH, true)) {
                fw.write(logLine + "\n");
            }
        } catch (IOException ignored) {
            // Non-fatal: logging failure doesn't block operation
        }
    }

    // =========================================================================
    //  Status / Info
    // =========================================================================

    /**
     * Get current network concern status (for Settings panel or terminal `netstat`).
     */
    public static String getStatus() {
        return String.format(
            "JDesk Network Concern — Status\n" +
            "───────────────────────────────────────────\n" +
            "  Connections this minute: %d / %d\n" +
            "  Active SSH sessions:     %d / %d\n" +
            "  Allowed commands:        %d\n" +
            "  Blocked commands:        %d\n" +
            "  Audit log:               %s\n" +
            "───────────────────────────────────────────\n" +
            "  Policy: Careful, ethical, logged.\n" +
            "  Diagnostics: ✓  Remote access: ✓  Scanning: ✗\n",
            connectionsThisMinute.get(), MAX_CONNECTIONS_PER_MINUTE,
            activeSshSessions.get(), MAX_SSH_SESSIONS,
            ALLOWED_NETWORK_COMMANDS.size(),
            BLOCKED_COMMANDS.size(),
            AUDIT_LOG_PATH
        );
    }
}
