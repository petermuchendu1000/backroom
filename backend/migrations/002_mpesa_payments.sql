-- Backroom — M-Pesa payment persistence
--
-- Runs automatically only on a FRESH database, after 01-init.sql (see the
-- docker-entrypoint-initdb.d mounts in docker-compose.yml). An already-initialised
-- database must have this applied by hand:
--
--     psql "$DATABASE_URL" -f backend/migrations/002_mpesa_payments.sql
--
-- Why this exists: there was no record of a payment attempt anywhere. Without one the
-- Safaricom callback cannot be tied to a user, cannot be made idempotent, and cannot be
-- told apart from a forged POST — the callback endpoint is necessarily public and
-- Daraja signs nothing.

-- ============================================
-- 'mpesa' was missing from the provider enum
-- ============================================
-- subscriptions.provider is NOT NULL and the enum held only stripe/google_play/apple,
-- so an M-Pesa subscription could not be inserted at all.
ALTER TYPE subscription_provider ADD VALUE IF NOT EXISTS 'mpesa';

-- ============================================
-- PAYMENTS
-- ============================================
DO $$ BEGIN
    CREATE TYPE payment_status AS ENUM ('pending', 'succeeded', 'failed', 'cancelled');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Correlation. checkout_request_id is what Safaricom echoes back in the callback and
    -- is the ONLY thing tying that callback to this attempt, so it is unique: a replayed
    -- callback cannot create a second payment, and a callback naming an id we never
    -- issued matches nothing.
    checkout_request_id TEXT NOT NULL UNIQUE,
    merchant_request_id TEXT,

    -- What was asked for, recorded BEFORE the money moves, so the callback is checked
    -- against what we expected rather than believed on its own word.
    plan TEXT NOT NULL,
    amount_kes INTEGER NOT NULL CHECK (amount_kes > 0),

    -- The payer. NOTE: this is the first directly identifying data the product stores.
    -- A payment cannot be reconciled, refunded or disputed without it, so it is kept
    -- deliberately rather than by accident, and it is NOT linked to any call record.
    phone_number TEXT NOT NULL,

    -- Outcome
    status payment_status NOT NULL DEFAULT 'pending',
    result_code INTEGER,
    result_desc TEXT,
    mpesa_receipt_number TEXT,
    completed_at TIMESTAMPTZ,

    -- The callback as received, for reconciliation against Safaricom's own statements.
    raw_callback JSONB
);

CREATE INDEX IF NOT EXISTS idx_payments_user ON payments(user_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_created ON payments(created_at);

COMMENT ON TABLE payments IS
    'One row per STK Push attempt. Written before the prompt reaches the phone so the callback has something to correlate against.';
COMMENT ON COLUMN payments.checkout_request_id IS
    'Safaricom CheckoutRequestID. Unique: this is the idempotency key for the callback.';
