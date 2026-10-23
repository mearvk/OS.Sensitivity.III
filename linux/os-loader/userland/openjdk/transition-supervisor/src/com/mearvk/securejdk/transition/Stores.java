package com.mearvk.securejdk.transition;

/**
 * Opens the failed-transition store described by the {@link SecurePolicy}
 * (mysql-bridge), degrading to the local JSONL fallback if MySQL is
 * unreachable. Per STP-0001 §6.3 the failure store must never crash the run, so
 * this never throws.
 */
public final class Stores {
    private Stores() {}

    public static FailedTransitionStore open(SecurePolicy policy) {
        String user = System.getenv().getOrDefault("SLEELA_MYSQL_USER", "jvm");
        String pass = System.getenv("SLEELA_MYSQL_PASSWORD");   // may be null; TLS + local socket
        try {
            MySqlStore s = new MySqlStore(policy.mysqlHost(), policy.mysqlPort(),
                    policy.mysqlDatabase(), user, pass, policy.mysqlTls());
            System.out.println("[stp] failure store: " + s.describe());
            return s;
        } catch (Throwable mysqlDown) {
            String fb = System.getenv().getOrDefault("SLEELA_STP_FALLBACK", "/tmp/sleela-stp-failures.jsonl");
            LocalJsonlStore s = new LocalJsonlStore(fb);
            System.out.println("[stp] MySQL unavailable (" + mysqlDown.getMessage()
                    + "); using fallback store: " + s.describe());
            return s;
        }
    }
}
