ALTER TABLE document
    ADD COLUMN gui_opened_at TIMESTAMPTZ,
    ADD COLUMN transmission_opened_sent_at TIMESTAMPTZ,
    ADD COLUMN transmission_opened_failed_at TIMESTAMPTZ;
