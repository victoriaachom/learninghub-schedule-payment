-- payment-service schema (paymentdb). Owned by payment-service only.
-- Payments context: billing profiles, saved cards, term tuition invoices and their lines.
-- username (Keycloak), term_id (schedule-service) and course_id (enrollment-service)
-- are shared ids from other contexts: plain text, no FK across databases.

CREATE TABLE billing_profile (
    id        BIGSERIAL PRIMARY KEY,
    username  TEXT NOT NULL UNIQUE,
    name      TEXT NOT NULL,
    email     TEXT,
    address   TEXT
);

CREATE TABLE payment_method (
    id          BIGSERIAL PRIMARY KEY,
    username    TEXT    NOT NULL,
    brand       TEXT    NOT NULL,
    last4       TEXT    NOT NULL,                   -- never the full card number
    expires     TEXT    NOT NULL,                   -- MM/YYYY
    is_default  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_payment_method_username ON payment_method (username);

CREATE TABLE invoice (
    id           BIGSERIAL PRIMARY KEY,
    username     TEXT          NOT NULL,
    term_id      TEXT          NOT NULL,
    description  TEXT          NOT NULL,
    amount       NUMERIC(10,2) NOT NULL,
    due_date     DATE          NOT NULL,
    status       TEXT          NOT NULL,            -- Paid | Unpaid (display status is computed)
    paid_on      DATE,
    method_id    BIGINT REFERENCES payment_method (id) ON DELETE SET NULL,
    CONSTRAINT uq_invoice_user_term UNIQUE (username, term_id)
);

CREATE TABLE invoice_line (
    id           BIGSERIAL PRIMARY KEY,
    invoice_id   BIGINT        NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    course_id    TEXT          NOT NULL,
    description  TEXT          NOT NULL,
    amount       NUMERIC(10,2) NOT NULL             -- $320 per course
);
CREATE INDEX idx_invoice_line_invoice ON invoice_line (invoice_id);
