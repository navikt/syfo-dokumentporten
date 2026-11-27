UPDATE document
SET delete_performed = now(),
    updated = now()
WHERE document_id = 'f1bb8a18-0d73-4081-aad3-be85f12ed28f'
  AND delete_performed IS NULL;
