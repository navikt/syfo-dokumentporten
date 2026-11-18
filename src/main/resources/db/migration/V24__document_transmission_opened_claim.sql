ALTER TABLE document
    ADD COLUMN IF NOT EXISTS transmission_opened_claim_token UUID,
    ADD COLUMN IF NOT EXISTS transmission_opened_claim_until TIMESTAMPTZ;
