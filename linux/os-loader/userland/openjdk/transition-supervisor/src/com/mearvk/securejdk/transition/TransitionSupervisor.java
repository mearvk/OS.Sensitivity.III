package com.mearvk.securejdk.transition;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.util.Set;
import java.util.concurrent.*;

/**
 * The SecureJDK 28 Transition Supervisor (STP-0001 server side).
 *
 * <p>Listens on a local UNIX-domain socket (default) and, optionally, a remote
 * TCP+TLS port. For each connection it performs the STP crypto handshake,
 * evaluates the submitted Sleela memory model against the SecureJDK policy
 * (class-load grades + memory-proxy budgets from {@code jvm-config.xml}), and:
 *
 * <ul>
 *   <li>on ADMIT — reserves a memorable supervised region, registers it on the
 *       Observer Circuit (monitorability / secure supervision), and returns a
 *       regioned, secured ACK; heartbeats until the client closes;</li>
 *   <li>on DENY — returns a DENY with a reason and records the failed
 *       transition in the private secured MySQL store for later Admin review.</li>
 * </ul>
 */
public final class TransitionSupervisor implements AutoCloseable {

    private final SecurePolicy policy;
    private final KeyPair identity;
    private final Set<String> authorizedClientHex;   // pinned client Ed25519 keys (hex)
    private final RegionAllocator regions = new RegionAllocator();
    private final ObserverCircuit observer;
    private final FailedTransitionStore store;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    public TransitionSupervisor(SecurePolicy policy, KeyPair identity,
                                Set<String> authorizedClientHex,
                                FailedTransitionStore store) {
        this.policy = policy;
        this.identity = identity;
        this.authorizedClientHex = authorizedClientHex;
        this.observer = new ObserverCircuit(policy.sshPort());
        this.store = store;
    }

    public PublicKey publicIdentity() { return identity.getPublic(); }
    public ObserverCircuit observer() { return observer; }

    // ---- local pipe (UNIX domain socket) ----------------------------------

    public void serveLocal(Path socketPath) throws IOException {
        Files.deleteIfExists(socketPath);
        Files.createDirectories(socketPath.getParent());
        ServerSocketChannel ss = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        ss.bind(UnixDomainSocketAddress.of(socketPath));
        harden(socketPath);
        System.out.println("[stp] supervisor listening on local pipe " + socketPath);
        System.out.println("[stp] identity ed25519=" + supervisorKeyHex());
        while (running) {
            SocketChannel ch = ss.accept();
            pool.submit(() -> handle(new ChannelStreams(ch), "local-pipe"));
        }
        ss.close();
    }

    // ---- remote (TCP + TLS 1.3) --------------------------------------------

    public void serveRemote(int port, javax.net.ssl.SSLServerSocketFactory tlsFactory) throws IOException {
        try (javax.net.ssl.SSLServerSocket ss =
                     (javax.net.ssl.SSLServerSocket) tlsFactory.createServerSocket(port)) {
            ss.setEnabledProtocols(new String[]{"TLSv1.3"});
            System.out.println("[stp] supervisor listening on remote TLS :" + port);
            while (running) {
                javax.net.ssl.SSLSocket s = (javax.net.ssl.SSLSocket) ss.accept();
                pool.submit(() -> {
                    try {
                        s.startHandshake();
                        byte[] exporter = tlsExporter(s);
                        handle(new SocketStreamsTls(s, exporter), "remote-tls");
                    } catch (Exception e) {
                        System.err.println("[stp] remote handshake failed: " + e.getMessage());
                    }
                });
            }
        }
    }

    // ---- per-connection handling -------------------------------------------

    private void handle(Streams io, String transport) {
        String source = "?", clientHex = null;
        Long grantedRegionId = null; String grantedRegionName = null;
        try (io) {
            SecureSession sess = new SecureSession(io.in(), io.out(), io.tlsExporter());
            String regionHint = sess.serverHandshake(identity, this::isAuthorized);
            clientHex = Crypto.hex(Crypto.ed25519Raw(sess.peerIdentity()));

            // Expect a TRANSITION_REQ
            SecureSession.Received req = sess.recvSealed();
            if (req.type() != Stp.T_TRANSITION_REQ) {
                sess.sendError("expected TRANSITION_REQ");
                return;
            }
            Wire.Map2 m = Wire.decode(req.body());
            source = m.textOr("source_name", "?");
            MemModel mm = MemModel.from(m);
            byte[] parseDigest = m.bytes("parse_digest");
            String programId = m.textOr("program_id", "");

            // ---- evaluate against SecureJDK policy ----
            Eval ev = evaluate(mm, regionHint);
            if (ev.admit()) {
                RegionAllocator.Region region = regions.reserve(ev.grade(), policy.budget(), mm.maxThreads());
                grantedRegionId = region.id(); grantedRegionName = region.name();
                observer.admit(region, source, clientHex);

                byte[] ackTag = Crypto.prf(sess.regionKey(), "ACK".getBytes(),
                        Wire.u64le(region.id()), parseDigest == null ? new byte[0] : parseDigest);
                Wire.Body ack = new Wire.Body()
                        .put("parse_ok", true)
                        .put("region_id", region.id())
                        .put("region_name", region.name())
                        .put("region_class", region.grade())
                        .put("ram_soft", region.ramSoft())
                        .put("ram_hard", region.ramHard())
                        .put("threads", (long) region.threads())
                        .put("ack_tag", ackTag)
                        .put("policy_digest", policy.digest())
                        .put("observer_endpoint", observer.endpoint())
                        .put("heartbeat_interval_ms", (long) Stp.DEFAULT_HEARTBEAT_MS);
                sess.sendSealed(Stp.T_ACK, 0, ack.encode());
                System.out.printf("[stp] ADMIT %s region=%s grade=%s threads=%d%n",
                        source, region.name(), region.grade(), region.threads());

                // Supervise: heartbeat until the client closes / releases.
                superviseLoop(sess, region.id());
                observer.release(region.id());
            } else {
                Wire.Body deny = new Wire.Body()
                        .put("parse_ok", false)
                        .put("reason", ev.reason())
                        .put("detail", ev.detail());
                sess.sendSealed(Stp.T_DENY, 0, deny.encode());
                System.out.printf("[stp] DENY  %s reason=%s (%s)%n", source, ev.reason(), ev.detail());
                recordFailure(programId, source, parseDigest, grantedRegionId, grantedRegionName,
                        ev.reason(), ev.detail(), mm, clientHex, transport);
            }
        } catch (SecurityException se) {
            System.err.println("[stp] security abort: " + se.getMessage());
            recordFailure("", source, null, grantedRegionId, grantedRegionName,
                    classify(se), se.getMessage(), MemModel.EMPTY, clientHex, transport);
        } catch (Exception e) {
            System.err.println("[stp] connection error: " + e.getMessage());
        }
    }

    /** Emit heartbeats to the observer circuit while the region is live. */
    private void superviseLoop(SecureSession sess, long regionId) {
        try {
            while (running) {
                SecureSession.Received r = sess.recvSealed();
                if (r.type() == Stp.T_HEARTBEAT) {
                    observer.beat(regionId);
                    sess.sendSealed(Stp.T_HEARTBEAT, 0, new Wire.Body().put("region_id", regionId).encode());
                } else if (r.type() == Stp.T_CLOSE) {
                    return;
                }
            }
        } catch (EOFException eof) {
            // client hung up; normal end of a supervised run
        } catch (Exception e) {
            System.err.println("[stp] supervise loop ended: " + e.getMessage());
        }
    }

    // ---- policy evaluation --------------------------------------------------

    private record Eval(boolean admit, String grade, String reason, String detail) {}

    private Eval evaluate(MemModel mm, String regionHint) {
        SecurePolicy.Budget b = policy.budget();
        // Thread budget (memory-proxy threads.hard)
        if (mm.maxThreads() > b.threadsHard())
            return new Eval(false, null, Stp.R_BUDGET_EXCEEDED,
                    "requested threads " + mm.maxThreads() + " > hard cap " + b.threadsHard());
        // Heap budget (ram.hard)
        if (b.ramHard() > 0 && mm.estHeap() > b.ramHard())
            return new Eval(false, null, Stp.R_BUDGET_EXCEEDED,
                    "estimated heap " + mm.estHeap() + " > ram hard " + b.ramHard());
        // Class-load grade: pick a grade by function count, enforce its max.
        String grade = gradeFor(mm.functions());
        long max = policy.gradeMax(grade);
        if (max >= 0 && mm.functions() > max)
            return new Eval(false, grade, Stp.R_GRADE_DENIED,
                    "functions " + mm.functions() + " exceed grade " + grade + " max " + max);
        return new Eval(true, grade, null, null);
    }

    /** Map a function count onto a SecureJDK class-load grade. */
    private String gradeFor(int functions) {
        if (functions <= 1) return "Main";
        if (functions <= 100) return "Manager";
        if (functions <= 150) return "Builder";
        if (functions <= 200) return "Archetype";
        if (functions <= 300) return "Gainer";
        if (functions <= 500) return "Inheritor";
        return "Ungraded";
    }

    private static String classify(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("key")) return Stp.R_UNKNOWN_PEER;
        if (m.contains("sig") || m.contains("tag")) return Stp.R_CRYPTO_FAIL;
        return Stp.R_POLICY;
    }

    private void recordFailure(String programId, String source, byte[] parseDigest,
                               Long regionId, String regionName, String reason, String detail,
                               MemModel mm, String clientHex, String transport) {
        try {
            FailedTransition ft = FailedTransition.create(
                    programId == null ? "" : programId,
                    source,
                    parseDigest == null ? "" : Crypto.hex(parseDigest),
                    regionId, regionName, reason, detail,
                    mm.globals(), mm.functions(), mm.codeLen(), mm.maxThreads(),
                    mm.locks(), mm.mailboxes(), mm.estHeap(),
                    clientHex, transport);
            long id = store.record(ft);
            System.out.printf("[stp] recorded failed transition #%d to %s (reason=%s)%n",
                    id, store.describe(), reason);
        } catch (Throwable t) {
            // §6.3: capture must never crash the supervisor.
            System.err.println("[stp] WARN: failed to record failure: " + t.getMessage());
        }
    }

    private boolean isAuthorized(byte[] clientRaw) {
        if (authorizedClientHex == null || authorizedClientHex.isEmpty()) return true; // open mode (dev)
        return authorizedClientHex.contains(Crypto.hex(clientRaw));
    }

    private String supervisorKeyHex() {
        try { return Crypto.hex(Crypto.ed25519Raw(identity.getPublic())); }
        catch (Exception e) { return "?"; }
    }

    private static void harden(Path socketPath) {
        try {
            Files.setPosixFilePermissions(socketPath,
                    java.util.EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (Exception ignore) { /* non-POSIX FS: best effort */ }
    }

    private static byte[] tlsExporter(javax.net.ssl.SSLSocket s) {
        try {
            // JDK 25 exposes exportKeyingMaterial on the extended session.
            var session = s.getSession();
            var m = session.getClass().getMethod("exportKeyingMaterial", String.class, byte[].class, int.class);
            return (byte[]) m.invoke(session, Stp.TLS_EXPORTER_LABEL, null, 32);
        } catch (Throwable t) {
            return null; // channel binding optional; STP envelope still protects the session
        }
    }

    @Override public void close() { running = false; pool.shutdownNow(); }

    // ---- the Sleela memory-model view (STP-0001 §5) ------------------------
    record MemModel(int globals, int functions, int codeLen, int maxThreads,
                    int locks, int mailboxes, long estHeap) {
        static final MemModel EMPTY = new MemModel(0,0,0,0,0,0,0);
        static MemModel from(Wire.Map2 m) {
            return new MemModel(
                    (int) m.u64Or("mm_globals", 0),
                    (int) m.u64Or("mm_functions", 0),
                    (int) m.u64Or("mm_code_len", 0),
                    (int) m.u64Or("mm_max_threads", 0),
                    (int) m.u64Or("mm_locks", 0),
                    (int) m.u64Or("mm_mailboxes", 0),
                    m.u64Or("mm_est_heap", 0));
        }
    }
}
