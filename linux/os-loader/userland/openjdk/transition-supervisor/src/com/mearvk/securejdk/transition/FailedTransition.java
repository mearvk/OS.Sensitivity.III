package com.mearvk.securejdk.transition;

/**
 * A failed / unacknowledged transition record (STP-0001 §6). Mirrors the
 * {@code failed_transitions} table. Created by the Sleela safe-trim fallback (or
 * by the supervisor on DENY) and reviewed later by an Admin.
 */
public record FailedTransition(
        long id,                 // 0 until persisted
        String programId,
        String sourceName,
        String parseDigest,
        Long regionId,           // nullable — the memorable region if one was granted
        String regionName,       // nullable
        String reason,           // one of Stp.R_*
        String detail,
        int mmGlobals,
        int mmFunctions,
        int mmCodeLen,
        int mmMaxThreads,
        int mmLocks,
        int mmMailboxes,
        long mmEstHeap,
        String clientKeyHex,     // nullable
        String transport,        // local-pipe | remote-tls | unknown
        String status            // NEW | REVIEWING | RESOLVED | DISMISSED
) {
    public static FailedTransition create(String programId, String sourceName, String parseDigest,
                                          Long regionId, String regionName, String reason, String detail,
                                          int g, int f, int code, int th, int lk, int mb, long heap,
                                          String clientKeyHex, String transport) {
        return new FailedTransition(0, programId, sourceName, parseDigest, regionId, regionName,
                reason, detail, g, f, code, th, lk, mb, heap, clientKeyHex, transport, "NEW");
    }
}
