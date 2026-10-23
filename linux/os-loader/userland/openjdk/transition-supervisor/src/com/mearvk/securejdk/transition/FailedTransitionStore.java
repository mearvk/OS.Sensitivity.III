package com.mearvk.securejdk.transition;

import java.util.List;

/**
 * The private secured store for failed transitions, plus the Admin review
 * surface. Two implementations: {@link MySqlStore} (the production private,
 * TLS-secured MySQL) and {@link LocalJsonlStore} (an append-only local fallback
 * used when MySQL is unreachable, so a store outage never crashes the
 * safe-trim run — STP-0001 §6.3).
 */
public interface FailedTransitionStore extends AutoCloseable {

    /** Record a failure; returns the assigned id (or a local id in fallback). */
    long record(FailedTransition ft);

    /** Admin: list rows in a given status (e.g. NEW), newest first. */
    List<FailedTransition> list(String status, int limit);

    /** Admin: fetch one row by id. */
    FailedTransition get(long id);

    /** Admin: transition a row's status and attach a note. */
    boolean review(long id, String newStatus, String admin, String note);

    /** A human label for logs. */
    String describe();

    @Override void close();
}
