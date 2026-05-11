ALTER TABLE varsel_instruks
    ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING',
    ADD COLUMN published_at TIMESTAMPTZ NULL,
    ADD COLUMN publish_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_publish_error TEXT NULL,
    ADD COLUMN updated TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX idx_varsel_instruks_status_created ON varsel_instruks(status, created);

-- Create a function to update the updated column
CREATE OR REPLACE FUNCTION update_updated_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create the trigger
CREATE TRIGGER update_varselinstruks_updated
    BEFORE UPDATE
    ON varsel_instruks
    FOR EACH ROW
EXECUTE FUNCTION update_updated_column();
