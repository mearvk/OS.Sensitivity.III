package com.mearvk.securejdk.transition;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Append-only local fallback store (STP-0001 §6.3): used when the private MySQL
 * is unreachable so that a failed transition is still captured for later Admin
 * import, and the safe-trim run never crashes because of a store outage.
 *
 * <p>Each failure is one JSON line in {@code $SLEELA_STP_FALLBACK} (default
 * {@code /tmp/sleela-stp-failures.jsonl}). The Admin CLI can read it and, when
 * MySQL is back, replay the lines into the {@code failed_transitions} table.
 */
public final class LocalJsonlStore implements FailedTransitionStore {

    private final Path path;
    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    public LocalJsonlStore(String path) {
        this.path = Paths.get(path == null ? "/tmp/sleela-stp-failures.jsonl" : path);
    }

    @Override
    public synchronized long record(FailedTransition f) {
        long id = seq.incrementAndGet();
        String line = json(id, f);
        try {
            Files.writeString(path, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException e) {
            // Truly last-resort: log to stderr but do not throw (safe-trim continues).
            System.err.println("[stp] WARN: could not write fallback failure store: " + e.getMessage());
        }
        return id;
    }

    @Override
    public List<FailedTransition> list(String status, int limit) {
        List<FailedTransition> out = new ArrayList<>();
        if (!Files.isRegularFile(path)) return out;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0 && out.size() < limit; i--) {
                FailedTransition ft = parse(lines.get(i));
                if (ft != null && (status == null || status.equals(ft.status()))) out.add(ft);
            }
        } catch (IOException ignore) {}
        return out;
    }

    @Override
    public FailedTransition get(long id) {
        return list(null, Integer.MAX_VALUE).stream().filter(f -> f.id() == id).findFirst().orElse(null);
    }

    @Override
    public boolean review(long id, String newStatus, String admin, String note) {
        // The JSONL fallback is append-only and not the review medium; the Admin
        // reviews in MySQL. We just append an audit line for traceability.
        record(FailedTransition.create("admin-audit", "review of #" + id, "",
                null, null, "OTHER", admin + ":" + newStatus + ":" + note,
                0, 0, 0, 0, 0, 0, 0, null, "unknown"));
        return true;
    }

    @Override public String describe() { return "jsonl:" + path; }
    @Override public void close() {}

    // ---- minimal JSON (no external lib) ----
    private static String json(long id, FailedTransition f) {
        StringBuilder b = new StringBuilder(256);
        b.append('{');
        kv(b, "id", id); b.append(',');
        ks(b, "ts", Instant.now().toString()); b.append(',');
        ks(b, "program_id", f.programId()); b.append(',');
        ks(b, "source_name", f.sourceName()); b.append(',');
        ks(b, "parse_digest", f.parseDigest()); b.append(',');
        if (f.regionName() != null) { ks(b, "region_name", f.regionName()); b.append(','); }
        ks(b, "reason", f.reason()); b.append(',');
        ks(b, "detail", f.detail() == null ? "" : f.detail()); b.append(',');
        kv(b, "mm_max_threads", f.mmMaxThreads()); b.append(',');
        kv(b, "mm_est_heap", f.mmEstHeap()); b.append(',');
        ks(b, "transport", f.transport()); b.append(',');
        ks(b, "status", f.status());
        b.append('}');
        return b.toString();
    }
    private static void kv(StringBuilder b, String k, long v) { b.append('"').append(k).append("\":").append(v); }
    private static void ks(StringBuilder b, String k, String v) {
        b.append('"').append(k).append("\":\"").append(esc(v)).append('"');
    }
    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    // very small parser: only fields we emit; good enough for admin listing
    private static FailedTransition parse(String line) {
        try {
            String src = field(line, "source_name");
            String reason = field(line, "reason");
            String region = field(line, "region_name");
            String status = field(line, "status");
            long id = Long.parseLong(numField(line, "id"));
            long heap = Long.parseLong(numField(line, "mm_est_heap"));
            int th = Integer.parseInt(numField(line, "mm_max_threads"));
            return new FailedTransition(id, field(line, "program_id"), src, field(line, "parse_digest"),
                    null, region, reason, field(line, "detail"), 0, 0, 0, th, 0, 0, heap,
                    null, field(line, "transport"), status == null ? "NEW" : status);
        } catch (RuntimeException e) { return null; }
    }
    private static String field(String line, String key) {
        int i = line.indexOf("\"" + key + "\":\"");
        if (i < 0) return null;
        int start = i + key.length() + 4;
        int end = line.indexOf('"', start);
        return end < 0 ? null : line.substring(start, end);
    }
    private static String numField(String line, String key) {
        int i = line.indexOf("\"" + key + "\":");
        if (i < 0) return "0";
        int start = i + key.length() + 3;
        int end = start;
        while (end < line.length() && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '-')) end++;
        return line.substring(start, end);
    }
}
