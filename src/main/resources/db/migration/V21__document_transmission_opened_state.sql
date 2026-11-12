ALTER TABLE document
    ADD COLUMN IF NOT EXISTS gui_opened_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS transmission_opened_sent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS transmission_opened_failed_at TIMESTAMPTZ;
