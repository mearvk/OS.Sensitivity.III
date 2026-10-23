-- ============================================================================
-- SecureJDK 28 — Transition Failure Store (private, secured MySQL)
-- ============================================================================
-- Backing store for FAILED / unacknowledged Sleela transitions (STP-0001 §6).
-- When a transition to the SecureJDK 28 supervisor fails (DENY / CRYPTO_FAIL /
-- TIMEOUT / UNREACHABLE), Sleela continues locally as a "safe trim" and the
-- failed result is recorded here for later review by an Admin.
--
-- Security posture (matches jvm-config.xml <mysql-bridge tls="true"> and the
-- <memory-proxy> mysql alerts):
--   * dedicated database, dedicated least-privilege app user
--   * TLS-required connections (REQUIRE SSL on the grant)
--   * no world access; reviewed by the Admin role only
--
-- Apply:  mysql --ssl-mode=REQUIRED -u root -p < schema.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS jvm_operand
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE jvm_operand;

-- ----------------------------------------------------------------------------
-- failed_transitions : one row per failed/unacknowledged transition attempt.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS failed_transitions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    created_at      TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    program_id      CHAR(64)        NOT NULL,           -- sha256 of source id
    source_name     VARCHAR(512)    NOT NULL,
    parse_digest    CHAR(64)        NOT NULL,           -- sha256 of the parse

    -- the memorable region name, if one was granted before the failure
    region_id       BIGINT UNSIGNED NULL,
    region_name     VARCHAR(128)    NULL,

    reason          ENUM('BUDGET_EXCEEDED','GRADE_DENIED','UNKNOWN_PEER',
                         'POLICY','CRYPTO_FAIL','TIMEOUT','UNREACHABLE',
                         'UNSUPPORTED_VERSION','OTHER') NOT NULL,
    detail          VARCHAR(1024)   NULL,

    -- Sleela memory-model snapshot (STP-0001 §5)
    mm_globals      INT UNSIGNED    NOT NULL DEFAULT 0,
    mm_functions    INT UNSIGNED    NOT NULL DEFAULT 0,
    mm_code_len     INT UNSIGNED    NOT NULL DEFAULT 0,
    mm_max_threads  INT UNSIGNED    NOT NULL DEFAULT 0,
    mm_locks        INT UNSIGNED    NOT NULL DEFAULT 0,
    mm_mailboxes    INT UNSIGNED    NOT NULL DEFAULT 0,
    mm_est_heap     BIGINT UNSIGNED NOT NULL DEFAULT 0,

    client_key      CHAR(64)        NULL,               -- hex Ed25519 id (12+ chars)
    transport       ENUM('local-pipe','remote-tls','unknown') NOT NULL DEFAULT 'unknown',

    -- Admin review workflow
    status          ENUM('NEW','REVIEWING','RESOLVED','DISMISSED')
                        NOT NULL DEFAULT 'NEW',
    admin_note      VARCHAR(2048)   NULL,
    reviewed_by     VARCHAR(128)    NULL,
    reviewed_at     TIMESTAMP(3)    NULL,

    PRIMARY KEY (id),
    KEY idx_status      (status),
    KEY idx_created     (created_at),
    KEY idx_program     (program_id),
    KEY idx_region_name (region_name)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- admin_audit : append-only log of Admin actions on the store.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_audit (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    at           TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    admin        VARCHAR(128)    NOT NULL,
    action       VARCHAR(64)     NOT NULL,          -- e.g. RESOLVE, DISMISS, NOTE
    transition_id BIGINT UNSIGNED NULL,
    detail       VARCHAR(2048)   NULL,
    PRIMARY KEY (id),
    KEY idx_at (at),
    CONSTRAINT fk_audit_txn FOREIGN KEY (transition_id)
        REFERENCES failed_transitions(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Least-privilege application + admin roles, TLS required.
-- (Set real passwords out of band; these are placeholders.)
-- ----------------------------------------------------------------------------
-- The supervisor writes failures with this account:
CREATE USER IF NOT EXISTS 'jvm'@'localhost'
    IDENTIFIED BY 'CHANGE_ME_APP' REQUIRE SSL;
GRANT INSERT, SELECT ON jvm_operand.failed_transitions TO 'jvm'@'localhost';
GRANT INSERT ON jvm_operand.admin_audit TO 'jvm'@'localhost';

-- The Admin reviews with this account:
CREATE USER IF NOT EXISTS 'jvm_admin'@'localhost'
    IDENTIFIED BY 'CHANGE_ME_ADMIN' REQUIRE SSL;
GRANT SELECT, UPDATE ON jvm_operand.failed_transitions TO 'jvm_admin'@'localhost';
GRANT SELECT, INSERT ON jvm_operand.admin_audit TO 'jvm_admin'@'localhost';

FLUSH PRIVILEGES;

-- Convenience view: the Admin's queue of unresolved failures, newest first.
CREATE OR REPLACE VIEW admin_review_queue AS
    SELECT id, created_at, source_name, region_name, reason, detail,
           mm_max_threads, mm_est_heap, transport, status
      FROM failed_transitions
     WHERE status IN ('NEW','REVIEWING')
     ORDER BY created_at DESC;
