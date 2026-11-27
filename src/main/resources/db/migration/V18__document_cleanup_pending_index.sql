DROP INDEX CONCURRENTLY IF EXISTS idx_document_cleanup_pending;

CREATE INDEX CONCURRENTLY idx_document_cleanup_pending
    ON document (created, id)
    WHERE content_deleted_at IS NULL;
