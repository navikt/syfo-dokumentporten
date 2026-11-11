ALTER TABLE document
    ADD COLUMN transmission_opened_claim_token UUID,
    ADD COLUMN transmission_opened_claim_until TIMESTAMPTZ;
