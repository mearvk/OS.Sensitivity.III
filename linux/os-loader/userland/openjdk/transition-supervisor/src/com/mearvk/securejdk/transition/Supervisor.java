package com.mearvk.securejdk.transition;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Entry point for the SecureJDK 28 Transition Supervisor.
 *
 * <pre>
 *   supervisor [--pipe &lt;path&gt;] [--remote &lt;port&gt;] [--config &lt;jvm-config.xml&gt;]
 *              [--identity &lt;ed25519.key&gt;] [--authorize &lt;hex,hex,...&gt;]
 * </pre>
 *
 * With no flags it listens on the default local pipe using an ephemeral
 * identity (dev mode), reading policy from the repo's jvm-config.xml and opening
 * the failure store (MySQL, else local JSONL fallback).
 */
public final class Supervisor {

    public static void main(String[] args) throws Exception {
        Map<String, String> opt = parse(args);

        // Policy from jvm-config.xml (mysql-bridge, memory-proxy, class-load-guard, jvm-circuit).
        String cfg = opt.getOrDefault("config",
                System.getenv().getOrDefault("SLEELA_JVM_CONFIG", "userland/openjdk/jvm-config.xml"));
        File cfgFile = new File(cfg);
        SecurePolicy policy = SecurePolicy.fromConfig(cfgFile.isFile() ? cfgFile : null);
        System.out.println("[stp] policy from " + (cfgFile.isFile() ? cfg : "<defaults>")
                + " — ram " + policy.budget().ramSoft() + "/" + policy.budget().ramHard()
                + ", threads " + policy.budget().threadsSoft() + "/" + policy.budget().threadsHard()
                + ", observer " + (policy.observerEnabled() ? "on :" + policy.sshPort() : "off"));

        // Long-term identity: load or generate.
        KeyPair id = loadOrCreateIdentity(opt.get("identity"));

        // Authorized client keys (pinned). Empty => open dev mode.
        Set<String> allow = new HashSet<>();
        if (opt.containsKey("authorize"))
            for (String h : opt.get("authorize").split(",")) if (!h.isBlank()) allow.add(h.trim());

        FailedTransitionStore store = Stores.open(policy);

        try (TransitionSupervisor sup = new TransitionSupervisor(policy, id, allow, store)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { sup.close(); store.close(); }));

            // Optional remote listener (TLS) in a background thread.
            if (opt.containsKey("remote")) {
                int port = Integer.parseInt(opt.get("remote"));
                new Thread(() -> {
                    try {
                        sup.serveRemote(port, (javax.net.ssl.SSLServerSocketFactory)
                                javax.net.ssl.SSLServerSocketFactory.getDefault());
                    } catch (IOException e) {
                        System.err.println("[stp] remote listener error: " + e.getMessage());
                    }
                }, "stp-remote").start();
            }

            // Local pipe (default path unless overridden).
            Path pipe = Paths.get(opt.getOrDefault("pipe", defaultPipe()));
            sup.serveLocal(pipe);
        }
    }

    static String defaultPipe() {
        String env = System.getenv("SLEELA_STP_PIPE");
        if (env != null && !env.isBlank()) return env;
        Path primaryParent = Paths.get(Stp.DEFAULT_PIPE_PRIMARY).getParent();
        if (primaryParent != null && Files.isWritable(primaryParent)) return Stp.DEFAULT_PIPE_PRIMARY;
        return Stp.DEFAULT_PIPE_FALLBACK;
    }

    /** Load a raw Ed25519 keypair (seed hex) or generate + print a fresh identity. */
    static KeyPair loadOrCreateIdentity(String path) throws Exception {
        if (path != null) {
            Path p = Paths.get(path);
            if (Files.isRegularFile(p)) {
                // Stored as base64 PKCS8 for the private key.
                byte[] pk8 = Base64.getDecoder().decode(Files.readString(p).trim());
                PrivateKey priv = KeyFactory.getInstance("Ed25519")
                        .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pk8));
                // Derive/attach the matching public key from the config sidecar (.pub) if present.
                Path pub = Paths.get(path + ".pub");
                if (Files.isRegularFile(pub)) {
                    byte[] raw = Base64.getDecoder().decode(Files.readString(pub).trim());
                    PublicKey publ = Crypto.ed25519FromRaw(raw);
                    return new KeyPair(publ, priv);
                }
            }
        }
        KeyPair kp = Crypto.newEd25519();
        System.out.println("[stp] generated ephemeral supervisor identity (pin this on clients):");
        System.out.println("[stp]   ed25519.pub = " + Crypto.hex(Crypto.ed25519Raw(kp.getPublic())));
        if (path != null) {
            Files.writeString(Paths.get(path),
                    Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()), StandardCharsets.UTF_8);
            Files.writeString(Paths.get(path + ".pub"),
                    Base64.getEncoder().encodeToString(Crypto.ed25519Raw(kp.getPublic())), StandardCharsets.UTF_8);
            System.out.println("[stp]   identity saved to " + path + " (+ .pub)");
        }
        return kp;
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) m.put(key, args[++i]);
                else m.put(key, "true");
            }
        }
        return m;
    }
}
