# SecureJDK 28 — Transition Supervisor + MySQL Admin (STP-0001)

This module is the **SecureJDK 28 side** of the Sleela ⇄ SecureJDK 28 secure
transition bridge. It lets a Sleela program run its memory model *under SecureJDK
28 supervision* — the security measures already declared in
[`../jvm-config.xml`](../jvm-config.xml): class-load grading, memory-proxy
budgets, the observer circuit (monitorability / secure supervision), and the
private MySQL bridge for admin review.

The protocol is **STP-0001** (spec: `impl/transition/STP.model` in the SLeeLa
repo). The Sleela client side lives there under `impl/transition/`.

## Components

- **Transition Supervisor** (`TransitionSupervisor`, `Supervisor` main) — listens
  on a local UNIX-domain socket (default) and, optionally, a remote **TLS 1.3**
  port. For each connection it runs the STP crypto handshake (Ed25519 / X25519 /
  HKDF-SHA-256 / ChaCha20-Poly1305), evaluates the submitted Sleela memory model
  against the SecureJDK policy, and either:
  - **ADMITs** — reserves a memorable supervised **region**, registers it on the
    **Observer Circuit**, and returns a regioned, secured **ACK**; or
  - **DENYs** — returns a reason and **records the failed transition** in the
    private secured **MySQL** for later Admin review.
- **MySQL Admin** (`Admin`) — the review interface (`list`, `show`, `resolve`,
  `dismiss`, `reviewing`) over the private `jvm_operand` database. Schema in
  [`sql/schema.sql`](sql/schema.sql) (TLS-required `jvm` app user + `jvm_admin`
  reviewer). Falls back to a local JSONL reader when MySQL is unavailable.

All crypto uses only the JDK — no third-party dependency — so it builds and runs
on the SecureJDK 28 toolchain (verified on JDK 25 in CI).

## Build & run

```sh
cd userland/openjdk/transition-supervisor
./build.sh                                   # compile to out/

# Run the supervisor on the default local pipe (generates/pins an identity):
./build.sh run --identity /etc/sleela/supervisor.key

# Also expose a remote TLS listener:
./build.sh run --remote 8443 --identity /etc/sleela/supervisor.key

# End-to-end in-process demo (ADMIT + DENY + Admin queue):
./build.sh demo

# Admin review of failed transitions:
./build.sh admin list NEW
./build.sh admin show <id>
./build.sh admin resolve <id> alice "reviewed: over-budget test job"
```

## MySQL

Apply the schema against the private, TLS-secured server:

```sh
mysql --ssl-mode=REQUIRED -u root -p < sql/schema.sql
```

Connection parameters come from `../jvm-config.xml` `<mysql-bridge>`
(host/port/database, `tls="true"` → `sslMode=REQUIRED`). Credentials from
`$SLEELA_MYSQL_USER` / `$SLEELA_MYSQL_PASSWORD`. When MySQL is unreachable the
supervisor and client both write failures to a JSONL fallback
(`$SLEELA_STP_FALLBACK`) so a store outage never crashes a safe-trim run; the
Admin CLI can read that fallback and it can be replayed into MySQL later.

> `out/` (compiled classes) is a build artifact and is git-ignored.
