CREATE INDEX idx_document_cleanup_pending
    ON document (created, id)
    WHERE content_deleted_at IS NULL;
