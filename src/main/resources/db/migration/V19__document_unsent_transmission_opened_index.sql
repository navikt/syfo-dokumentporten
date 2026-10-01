CREATE INDEX idx_document_unsent_transmission_opened
    ON document (gui_opened_at)
    WHERE gui_opened_at IS NOT NULL
        AND transmission_opened_sent_at IS NULL
        AND transmission_opened_failed_at IS NULL
        AND delete_performed IS NULL;
