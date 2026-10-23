package com.mearvk.securejdk.transition;

import java.io.File;
import java.util.List;

/**
 * The MySQL Admin — the review interface for failed / unacknowledged Sleela
 * transitions captured under STP-0001 §6. Run against the private secured MySQL
 * (or the JSONL fallback for listing).
 *
 * <pre>
 *   admin list [status]                 list rows (default status NEW), newest first
 *   admin show &lt;id&gt;                      show one row in full
 *   admin resolve &lt;id&gt; &lt;admin&gt; &lt;note&gt;    mark RESOLVED with a note
 *   admin dismiss &lt;id&gt; &lt;admin&gt; &lt;note&gt;    mark DISMISSED with a note
 *   admin reviewing &lt;id&gt; &lt;admin&gt; &lt;note&gt;  mark REVIEWING
 * </pre>
 *
 * Config (mysql-bridge) is read from {@code $SLEELA_JVM_CONFIG} or the repo's
 * {@code userland/openjdk/jvm-config.xml}; credentials from
 * {@code $SLEELA_MYSQL_USER} / {@code $SLEELA_MYSQL_PASSWORD}.
 */
public final class Admin {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { usage(); System.exit(2); }

        SecurePolicy policy = loadPolicy();
        String cmd = args[0];

        try (FailedTransitionStore store = openAdminStore(policy)) {
            switch (cmd) {
                case "list" -> {
                    String status = args.length > 1 ? args[1] : "NEW";
                    List<FailedTransition> rows = store.list(status, 50);
                    System.out.printf("Admin review queue (%s) — %d row(s) — store %s%n",
                            status, rows.size(), store.describe());
                    System.out.println("  id  | region                       | reason           | source");
                    System.out.println("------+------------------------------+------------------+---------------------");
                    for (FailedTransition f : rows) {
                        System.out.printf("%5d | %-28s | %-16s | %s%n",
                                f.id(),
                                f.regionName() == null ? "(none)" : f.regionName(),
                                f.reason(), f.sourceName());
                    }
                }
                case "show" -> {
                    long id = Long.parseLong(args[1]);
                    FailedTransition f = store.get(id);
                    if (f == null) { System.out.println("no such transition: " + id); break; }
                    show(f);
                }
                case "resolve", "dismiss", "reviewing" -> {
                    long id = Long.parseLong(args[1]);
                    String admin = args.length > 2 ? args[2] : "admin";
                    String note = args.length > 3 ? String.join(" ",
                            java.util.Arrays.copyOfRange(args, 3, args.length)) : "";
                    String status = switch (cmd) {
                        case "resolve" -> "RESOLVED";
                        case "dismiss" -> "DISMISSED";
                        default -> "REVIEWING";
                    };
                    boolean ok = store.review(id, status, admin, note);
                    System.out.println(ok ? ("#" + id + " -> " + status + " by " + admin)
                                          : ("no such transition: " + id));
                }
                default -> { usage(); System.exit(2); }
            }
        }
    }

    private static void show(FailedTransition f) {
        System.out.println("Failed transition #" + f.id());
        System.out.println("  status        : " + f.status());
        System.out.println("  reason        : " + f.reason() + (f.detail() != null ? " (" + f.detail() + ")" : ""));
        System.out.println("  source        : " + f.sourceName());
        System.out.println("  program_id    : " + f.programId());
        System.out.println("  parse_digest  : " + f.parseDigest());
        System.out.println("  region        : " + (f.regionName() == null ? "(none granted)" : f.regionName()));
        System.out.println("  transport     : " + f.transport());
        System.out.println("  client_key    : " + (f.clientKeyHex() == null ? "(n/a)" : f.clientKeyHex()));
        System.out.println("  memory model  : globals=" + f.mmGlobals() + " funcs=" + f.mmFunctions()
                + " code=" + f.mmCodeLen() + " threads=" + f.mmMaxThreads()
                + " locks=" + f.mmLocks() + " mailboxes=" + f.mmMailboxes()
                + " est_heap=" + f.mmEstHeap());
    }

    /** For review we prefer the admin MySQL user; fall back to listing JSONL. */
    private static FailedTransitionStore openAdminStore(SecurePolicy policy) {
        String user = System.getenv().getOrDefault("SLEELA_MYSQL_USER", "jvm_admin");
        String pass = System.getenv("SLEELA_MYSQL_PASSWORD");
        try {
            return new MySqlStore(policy.mysqlHost(), policy.mysqlPort(),
                    policy.mysqlDatabase(), user, pass, policy.mysqlTls());
        } catch (Throwable t) {
            String fb = System.getenv().getOrDefault("SLEELA_STP_FALLBACK", "/tmp/sleela-stp-failures.jsonl");
            System.out.println("[admin] MySQL unavailable (" + t.getMessage() + "); reading fallback " + fb);
            return new LocalJsonlStore(fb);
        }
    }

    static SecurePolicy loadPolicy() throws Exception {
        String cfg = System.getenv("SLEELA_JVM_CONFIG");
        File f = cfg != null ? new File(cfg) : new File("userland/openjdk/jvm-config.xml");
        return SecurePolicy.fromConfig(f.isFile() ? f : null);
    }

    private static void usage() {
        System.out.println("""
            SecureJDK 28 Transition Admin (MySQL)
              admin list [status]                 list failed transitions (default NEW)
              admin show <id>                     show one transition
              admin resolve  <id> <admin> <note>  mark RESOLVED
              admin dismiss  <id> <admin> <note>  mark DISMISSED
              admin reviewing <id> <admin> <note> mark REVIEWING
            """);
    }
}
