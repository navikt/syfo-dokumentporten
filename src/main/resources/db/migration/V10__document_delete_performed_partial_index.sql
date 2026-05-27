CREATE INDEX idx_document_active_status_created
    ON document (status, created)
    WHERE delete_performed IS NULL;
