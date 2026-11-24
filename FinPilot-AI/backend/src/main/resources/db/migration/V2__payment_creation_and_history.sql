-- Milestone 3: payment creation, idempotency, state machine history.
-- New columns are nullable so rows that already exist (e.g. hand-inserted demo rows) stay valid.
ALTER TABLE payments
    ADD COLUMN idempotency_key  VARCHAR(100),
    ADD COLUMN request_hash     VARCHAR(64),
    ADD COLUMN debtor_account   VARCHAR(34),
    ADD COLUMN creditor_account VARCHAR(34),
    ADD COLUMN creditor_name    VARCHAR(140),
    ADD COLUMN description      VARCHAR(255),
    ADD COLUMN failure_reason   VARCHAR(500),
    ADD COLUMN correlation_id   VARCHAR(64),
    ADD COLUMN version          BIGINT NOT NULL DEFAULT 0;

-- Idempotency: one payment per client-supplied key. NULLs (legacy rows) never conflict.
ALTER TABLE payments ADD CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key);

ALTER TABLE payments ADD CONSTRAINT ck_payments_status
    CHECK (status IN ('CREATED', 'VALIDATED', 'PROCESSING', 'REQUIRES_REVIEW', 'COMPLETED', 'FAILED'));

CREATE INDEX idx_payments_currency ON payments (currency);
CREATE INDEX idx_payments_amount ON payments (amount);

CREATE TABLE payment_status_history (
    id             UUID         PRIMARY KEY,
    payment_id     UUID         NOT NULL REFERENCES payments (id),
    sequence_no    INTEGER      NOT NULL,
    from_status    VARCHAR(30),
    to_status      VARCHAR(30)  NOT NULL,
    reason         VARCHAR(500),
    correlation_id VARCHAR(64),
    changed_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_psh_payment_sequence UNIQUE (payment_id, sequence_no)
);

CREATE SEQUENCE payment_reference_seq START WITH 1000;
