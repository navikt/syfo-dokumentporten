BEGIN;
ALTER TABLE dialog
ADD COLUMN dialogporten_api_only BOOLEAN DEFAULT false;

UPDATE dialog
SET dialogporten_api_only = true
WHERE created <= '2026-05-04' ::timestamptz;
COMMIT;
