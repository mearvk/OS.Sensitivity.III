package com.mearvk.securejdk.transition;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * The Observer Grade Circuit — the monitorability + secure-supervision surface
 * (§ jvm-config {@code <jvm-circuit>}). While a region is live the supervisor
 * emits heartbeats here; an operator (SSH/socket console, out of scope for this
 * module) can enumerate live supervised regions and their last-seen times.
 *
 * <p>This is the "secure supervision / admin" view Sleela is calling for: every
 * admitted transition is observable by region name for the life of the run.
 */
public final class ObserverCircuit {

    public record Live(long regionId, String regionName, String source,
                       String clientKeyHex, Instant admittedAt, Instant lastBeat) {}

    private final Map<Long, Live> live = new ConcurrentHashMap<>();
    private final int sshPort;

    public ObserverCircuit(int sshPort) { this.sshPort = sshPort; }

    public String endpoint() { return "observer://localhost:" + sshPort; }

    public void admit(RegionAllocator.Region r, String source, String clientKeyHex) {
        Instant now = Instant.now();
        live.put(r.id(), new Live(r.id(), r.name(), source, clientKeyHex, now, now));
        log("ADMIT", r.name(), source + " (" + shortKey(clientKeyHex) + ")");
    }

    public void beat(long regionId) {
        live.computeIfPresent(regionId, (k, v) ->
            new Live(v.regionId(), v.regionName(), v.source(), v.clientKeyHex(), v.admittedAt(), Instant.now()));
    }

    public void release(long regionId) {
        Live v = live.remove(regionId);
        if (v != null) log("RELEASE", v.regionName(), v.source());
    }

    public Map<Long, Live> snapshot() { return Map.copyOf(live); }

    private static String shortKey(String hex) {
        return hex == null ? "?" : hex.substring(0, Math.min(12, hex.length()));
    }

    private void log(String ev, String region, String detail) {
        System.out.printf("[observer %s] %-8s region=%s %s%n",
                Instant.now(), ev, region, detail);
    }
}
