UPDATE document
SET delete_performed = now(),
    updated = now()
WHERE document_id IN ('f73dcff9-b0b0-4d29-bba7-6bb9be5d8ae7', 'b173d297-8a5f-4760-96bd-f19cc33c8f5e')
  AND delete_performed IS NULL;
