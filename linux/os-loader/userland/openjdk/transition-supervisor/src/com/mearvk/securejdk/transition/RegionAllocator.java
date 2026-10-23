package com.mearvk.securejdk.transition;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Allocates supervised memory regions for admitted Sleela memory models and
 * gives each a "memorable" name (§4). The name is a deterministic
 * adjective+gem mnemonic derived from the region id, so operators and Admin can
 * refer to a specific transition by name (e.g. {@code reg-emerald-0007}).
 */
public final class RegionAllocator {

    private static final String[] ADJ = {
        "azure", "amber", "crimson", "verdant", "onyx", "ivory", "cobalt",
        "scarlet", "golden", "silver", "violet", "teal", "coral", "jade"
    };
    private static final String[] GEM = {
        "emerald", "ruby", "sapphire", "topaz", "opal", "garnet", "pearl",
        "amethyst", "beryl", "zircon", "quartz", "jasper", "onyxstone", "peridot"
    };

    private final AtomicLong next = new AtomicLong(1);

    public record Region(long id, String name, String grade,
                         long ramSoft, long ramHard, int threads) {}

    /** Reserve a region for a memory model of the given grade + thread request. */
    public Region reserve(String grade, SecurePolicy.Budget budget, int threadsRequested) {
        long id = next.getAndIncrement();
        int threads = Math.min(threadsRequested, budget.threadsHard());
        return new Region(id, mnemonic(id), grade, budget.ramSoft(), budget.ramHard(), threads);
    }

    /** Deterministic memorable name for a region id. */
    public static String mnemonic(long id) {
        String adj = ADJ[(int) (Math.floorMod(id * 2654435761L, ADJ.length))];
        String gem = GEM[(int) (Math.floorMod(id, GEM.length))];
        return String.format("reg-%s-%s-%04d", adj, gem, id);
    }
}
