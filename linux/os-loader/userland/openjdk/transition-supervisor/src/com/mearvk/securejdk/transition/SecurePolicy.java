package com.mearvk.securejdk.transition;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The SecureJDK 28 policy, sourced from {@code jvm-config.xml} (the same file
 * that already declares the ClassLoadGuard grades, the memory-proxy budgets,
 * the observer circuit, and the mysql-bridge). This is the security posture the
 * supervisor applies to a Sleela transition: it is what Sleela is "calling for"
 * — class-load grading, memory budgets, monitorability, and admin capture.
 *
 * <p>Parsing is hardened per the {@code <xml-config-reader>} security block:
 * DTDs are rejected and external entities disabled.
 */
public final class SecurePolicy {

    /** A memory-proxy budget (subset relevant to a Sleela memory model). */
    public record Budget(long ramSoft, long ramHard, int threadsSoft, int threadsHard) {}

    private final Map<String, Long> grades = new LinkedHashMap<>();  // grade -> max classes (-1 = unlimited)
    private Budget budget = new Budget(512L << 20, 2L << 30, 64, 256);
    private long classGlobalMax = 5000;
    private boolean observerEnabled = true;
    private int sshPort = 2222;
    private String mysqlDatabase = "jvm_operand";
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private boolean mysqlTls = true;

    public Budget budget()          { return budget; }
    public long classGlobalMax()    { return classGlobalMax; }
    public boolean observerEnabled(){ return observerEnabled; }
    public int sshPort()            { return sshPort; }
    public String mysqlDatabase()   { return mysqlDatabase; }
    public String mysqlHost()       { return mysqlHost; }
    public int mysqlPort()          { return mysqlPort; }
    public boolean mysqlTls()       { return mysqlTls; }

    /** Max classes for a grade; unlimited (-1) if unknown. */
    public long gradeMax(String grade) { return grades.getOrDefault(grade, -1L); }

    /** Grades in declaration order (Main, Manager, Archetype, ...). */
    public Map<String, Long> grades() { return grades; }

    /** Load the default (built-in) policy — used when no config file is present. */
    public static SecurePolicy defaults() {
        SecurePolicy p = new SecurePolicy();
        p.grades.put("Main", -1L);
        p.grades.put("Manager", 100L);
        p.grades.put("Archetype", 200L);
        p.grades.put("Builder", 150L);
        p.grades.put("Inheritor", 500L);
        p.grades.put("Gainer", 300L);
        p.grades.put("Substitute", 200L);
        p.grades.put("Ungraded", 2000L);
        return p;
    }

    /** Parse a {@code jvm-config.xml}. Falls back to defaults for missing bits. */
    public static SecurePolicy fromConfig(File xml) throws Exception {
        SecurePolicy p = defaults();
        if (xml == null || !xml.isFile()) return p;

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // Harden per <xml-config-reader><security><reject-dtd>.
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setExpandEntityReferences(false);
        Document d = f.newDocumentBuilder().parse(xml);

        // class-load-guard grades + global-max
        Element clg = first(d, "class-load-guard");
        if (clg != null) {
            Element gm = firstChild(clg, "global-max");
            if (gm != null) p.classGlobalMax = parseSize(attr(gm, "value", "5000"));
            p.grades.clear();
            NodeList gl = clg.getElementsByTagName("grade");
            for (int i = 0; i < gl.getLength(); i++) {
                Element g = (Element) gl.item(i);
                String name = attr(g, "name", "Ungraded");
                String max = attr(g, "max", "unlimited");
                p.grades.put(name, "unlimited".equalsIgnoreCase(max) ? -1L : parseSize(max));
            }
        }

        // memory-proxy default-budget
        Element mp = first(d, "memory-proxy");
        if (mp != null) {
            Element db = firstChild(mp, "default-budget");
            if (db != null) {
                Element ram = firstChild(db, "ram");
                Element th  = firstChild(db, "threads");
                long rs = ram != null ? parseSize(attr(ram, "soft", "512m")) : p.budget.ramSoft();
                long rh = ram != null ? parseSize(attr(ram, "hard", "2g"))   : p.budget.ramHard();
                int ts = th  != null ? (int) parseSize(attr(th, "soft", "64")) : p.budget.threadsSoft();
                int tHard = th != null ? (int) parseSize(attr(th, "hard", "256")) : p.budget.threadsHard();
                p.budget = new Budget(rs, rh, ts, tHard);
            }
        }

        // observer circuit
        Element circ = first(d, "jvm-circuit");
        if (circ != null) {
            p.observerEnabled = "true".equalsIgnoreCase(attr(circ, "enabled", "true"));
            Element ssh = firstChild(circ, "ssh");
            if (ssh != null) p.sshPort = (int) parseSize(attr(ssh, "port", "2222"));
        }

        // mysql-bridge
        Element mb = first(d, "mysql-bridge");
        if (mb != null) {
            Element conn = firstChild(mb, "connection");
            if (conn != null) {
                p.mysqlHost = attr(conn, "host", "localhost");
                p.mysqlPort = (int) parseSize(attr(conn, "port", "3306"));
                p.mysqlDatabase = attr(conn, "database", "jvm_operand");
                p.mysqlTls = "true".equalsIgnoreCase(attr(conn, "tls", "true"));
            }
        }
        return p;
    }

    /** SHA-256 of the applied policy, for the ACK's policy_digest. */
    public byte[] digest() throws Exception {
        StringBuilder sb = new StringBuilder("SecureJDK28|");
        sb.append("globalMax=").append(classGlobalMax).append('|');
        grades.forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        sb.append("|ram=").append(budget.ramSoft()).append('/').append(budget.ramHard());
        sb.append("|threads=").append(budget.threadsSoft()).append('/').append(budget.threadsHard());
        return Crypto.sha256(sb.toString().getBytes());
    }

    // ---- tiny DOM helpers ----
    private static Element first(Document d, String tag) {
        NodeList l = d.getElementsByTagName(tag); return l.getLength() > 0 ? (Element) l.item(0) : null;
    }
    private static Element firstChild(Element e, String tag) {
        NodeList l = e.getElementsByTagName(tag); return l.getLength() > 0 ? (Element) l.item(0) : null;
    }
    private static String attr(Element e, String name, String def) {
        String v = e.getAttribute(name); return (v == null || v.isEmpty()) ? def : v;
    }

    /** Parse "512m" / "2g" / "5000" / "unlimited" into a byte/scalar count. */
    static long parseSize(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty() || "unlimited".equalsIgnoreCase(s)) return -1;
        long mult = 1;
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        if (last == 'k') { mult = 1L << 10; s = s.substring(0, s.length() - 1); }
        else if (last == 'm') { mult = 1L << 20; s = s.substring(0, s.length() - 1); }
        else if (last == 'g') { mult = 1L << 30; s = s.substring(0, s.length() - 1); }
        return Long.parseLong(s.trim()) * mult;
    }
}
