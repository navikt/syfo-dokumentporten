BEGIN;
ALTER TABLE dialog
ADD COLUMN dialogporten_api_only BOOLEAN DEFAULT false;

UPDATE dialog
SET dialogporten_api_only = true
WHERE created < '2026-05-05 00:00:00+00' ::timestamptz;
COMMIT;
