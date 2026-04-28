ALTER TABLE varsel_instruks
    ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING',
    ADD COLUMN published_at TIMESTAMPTZ NULL,
    ADD COLUMN publish_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_publish_error TEXT NULL;

CREATE INDEX idx_varsel_instruks_status_created ON varsel_instruks(status, created);
