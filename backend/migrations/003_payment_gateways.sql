-- Ongero — more than one payment gateway
--
-- Runs automatically only on a FRESH database, after 02-mpesa-payments.sql (see the
-- docker-entrypoint-initdb.d mounts in docker-compose.yml). An already-initialised
-- database must have this applied by hand:
--
--     psql "$DATABASE_URL" -f backend/migrations/003_payment_gateways.sql
--
-- Why this exists: payments were written as though Safaricom Daraja were the only way
-- money could arrive. We now collect through PalPluss, an aggregator that fronts Daraja,
-- and may add others. Two things follow.
--
-- First, a payment row has to say which gateway issued its correlation id. Without that,
-- a webhook from gateway A could be matched against a row created by gateway B. The ids
-- are different shapes today (a PalPluss transaction id is a UUID, a Daraja
-- CheckoutRequestID looks like ws_CO_191220191020363925) so a collision is not plausible,
-- but "not plausible" is not a constraint. Matching is scoped by provider instead.
--
-- Second, the reference WE generate now matters. PalPluss echoes it back in the webhook
-- as external_reference and caps it at 12 characters, so it is stored rather than
-- recomputed, and it is what a human reconciling a statement will search for.

-- ============================================
-- 'palpluss' was missing from the provider enum
-- ============================================
-- subscriptions.provider is NOT NULL. A subscription bought through PalPluss records the
-- gateway, not the rail: the money still moves over M-Pesa, but PalPluss is who we would
-- ask about it. ALTER TYPE ... ADD VALUE is its own statement on purpose — in PostgreSQL
-- it cannot be run inside a transaction block that later uses the new value.
ALTER TYPE subscription_provider ADD VALUE IF NOT EXISTS 'palpluss';

-- ============================================
-- PAYMENTS: which gateway, and our own reference
-- ============================================

-- Deliberately TEXT and not an enum. Adding a gateway should be a deploy, not a
-- migration that has to run ALTER TYPE outside a transaction on a live database. The
-- allowed values are governed by the PaymentProvider enum in the application
-- (src/modules/payments/payment-gateway.ts); the default backfills every row that
-- existed before this migration, all of which were Daraja.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS provider TEXT NOT NULL DEFAULT 'daraja';

-- Our reference, generated before the gateway is called and echoed back by it.
-- Nullable because rows written before this migration never had one.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS reference TEXT;

-- A webhook arrives knowing its own gateway and the correlation id; that pair is the
-- lookup. checkout_request_id stays UNIQUE on its own — this index is for the read path,
-- not for integrity.
CREATE INDEX IF NOT EXISTS idx_payments_provider_checkout
    ON payments(provider, checkout_request_id);

CREATE INDEX IF NOT EXISTS idx_payments_reference ON payments(reference);

COMMENT ON COLUMN payments.provider IS
    'Which gateway issued checkout_request_id: daraja (Safaricom direct) or palpluss.';
COMMENT ON COLUMN payments.reference IS
    'The short reference we generated and sent. PalPluss echoes it back as external_reference; capped at 12 characters by its API.';
COMMENT ON COLUMN payments.checkout_request_id IS
    'The gateway''s id for this attempt, and the idempotency key for its webhook. A Daraja CheckoutRequestID or a PalPluss transaction id, per the provider column.';
COMMENT ON COLUMN payments.mpesa_receipt_number IS
    'M-Pesa confirmation number, whichever gateway reported it. Proof of payment on a statement.';
