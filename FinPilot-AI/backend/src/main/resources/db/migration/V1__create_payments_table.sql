-- Initial schema. Rows are created by later milestones; this milestone only reads.
CREATE TABLE payments (
    id                UUID           PRIMARY KEY,
    payment_reference VARCHAR(40)    NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    status            VARCHAR(30)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_reference UNIQUE (payment_reference),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payments_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_payments_created_at ON payments (created_at DESC);
CREATE INDEX idx_payments_status ON payments (status);
