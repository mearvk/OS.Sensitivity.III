package com.mearvk.securejdk.transition;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * In-process end-to-end proof of the STP-0001 bridge over a real local UNIX
 * pipe: starts the supervisor, runs a Java reference client through the full
 * crypto handshake, and demonstrates BOTH outcomes —
 *   (1) ADMIT: a small memory model gets a regioned, secured ACK; and
 *   (2) DENY : an over-budget memory model is denied and recorded to the
 *              failure store (MySQL, or JSONL fallback) for Admin review.
 *
 * This is the reference the C client in the SLeeLa repo is built against.
 */
public final class LoopbackDemo {

    public static void main(String[] args) throws Exception {
        Path pipe = Files.createTempDirectory("stp-demo").resolve("stp.sock");

        SecurePolicy policy = SecurePolicy.fromConfig(locateConfig());
        KeyPair supId = Crypto.newEd25519();
        FailedTransitionStore store = Stores.open(policy);

        TransitionSupervisor sup = new TransitionSupervisor(policy, supId, Set.of(), store);
        Thread server = new Thread(() -> {
            try { sup.serveLocal(pipe); } catch (Exception e) { /* stopped */ }
        }, "stp-demo-supervisor");
        server.setDaemon(true);
        server.start();
        Thread.sleep(300);  // let it bind

        PublicKey pinnedServer = supId.getPublic();

        System.out.println("\n================ CASE 1: ADMIT (small memory model) ================");
        runClient(pipe, pinnedServer,
                new TransitionSupervisor.MemModel(2, 4, 120, 8, 1, 1, 4L << 20),
                "http/Router.sleela");

        System.out.println("\n================ CASE 2: DENY (threads over budget) ================");
        runClient(pipe, pinnedServer,
                new TransitionSupervisor.MemModel(1, 1, 999999, 4096, 32, 32, 64L << 30),
                "runaway/Fork.sleela");

        Thread.sleep(200);
        System.out.println("\n================ ADMIN: review queue ================");
        for (FailedTransition f : store.list("NEW", 10)) {
            System.out.printf("  #%d  region=%s  reason=%s  source=%s%n",
                    f.id(), f.regionName(), f.reason(), f.sourceName());
        }
        store.close();
        sup.close();
        System.out.println("\n[demo] done.");
    }

    /** A minimal reference client: handshake, submit a memory model, print the outcome. */
    static void runClient(Path pipe, PublicKey pinnedServer,
                          TransitionSupervisor.MemModel mm, String source) throws Exception {
        try (SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            ch.connect(UnixDomainSocketAddress.of(pipe));
            InputStream in = new BufferedInputStream(Channels.newInputStream(ch));
            OutputStream out = new BufferedOutputStream(Channels.newOutputStream(ch));

            SecureSession sess = new SecureSession(in, out, null);
            KeyPair clientId = Crypto.newEd25519();
            sess.clientHandshake(clientId, pinnedServer, "sleela");

            byte[] parseDigest = Crypto.sha256(("parse|" + source).getBytes());
            Wire.Body req = new Wire.Body()
                    .put("program_id", Crypto.hex(Crypto.sha256(source.getBytes())).substring(0, 16))
                    .put("source_name", source)
                    .put("parse_digest", parseDigest)
                    .put("mm_globals", (long) mm.globals())
                    .put("mm_functions", (long) mm.functions())
                    .put("mm_code_len", (long) mm.codeLen())
                    .put("mm_max_threads", (long) mm.maxThreads())
                    .put("mm_locks", (long) mm.locks())
                    .put("mm_mailboxes", (long) mm.mailboxes())
                    .put("mm_est_heap", mm.estHeap())
                    .put("sysdepth", 3024L)
                    .put("degreemax", 4L)
                    .put("want", "monitorability,admin,supervision");
            sess.sendSealed(Stp.T_TRANSITION_REQ, 0, req.encode());

            SecureSession.Received r = sess.recvSealed();
            Wire.Map2 m = Wire.decode(r.body());
            if (r.type() == Stp.T_ACK && m.bool("parse_ok")) {
                // Verify the regioned, secured ack tag (§4.2).
                long regionId = m.u64("region_id");
                byte[] wantTag = Crypto.prf(sess.regionKey(), "ACK".getBytes(),
                        Wire.u64le(regionId), parseDigest);
                boolean tagOk = MessageDigest.isEqual(wantTag, m.bytes("ack_tag"));
                System.out.println("  CLIENT: ACK verified=" + tagOk
                        + " region=" + m.text("region_name")
                        + " class=" + m.text("region_class")
                        + " threads=" + m.u64("threads")
                        + " observer=" + m.text("observer_endpoint"));
                System.out.println("  CLIENT: -> proceed UNDER SUPERVISION in region "
                        + m.text("region_name"));
                // send a heartbeat then close
                sess.sendSealed(Stp.T_HEARTBEAT, 0, new Wire.Body().put("region_id", regionId).encode());
                sess.recvSealed(); // heartbeat echo
                sess.sendSealed(Stp.T_CLOSE, Stp.F_LAST, new Wire.Body().put("bye", true).encode());
            } else {
                System.out.println("  CLIENT: DENY reason=" + m.textOr("reason", "?")
                        + " (" + m.textOr("detail", "") + ")");
                System.out.println("  CLIENT: -> FALLBACK to local safe-trim; failure recorded for Admin");
            }
        }
    }

    static File locateConfig() {
        for (String p : new String[]{
                System.getenv("SLEELA_JVM_CONFIG"),
                "userland/openjdk/jvm-config.xml",
                "../jvm-config.xml",
                "../../jvm-config.xml"}) {
            if (p != null && new File(p).isFile()) return new File(p);
        }
        return null;
    }
}
