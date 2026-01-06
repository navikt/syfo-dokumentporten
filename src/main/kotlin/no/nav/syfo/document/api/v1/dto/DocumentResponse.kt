package no.nav.syfo.document.api.v1.dto

import no.nav.syfo.document.db.DocumentStatus
import java.time.Instant
import java.util.UUID

data class DocumentResponse(
    val documentId: UUID,
    val type: DocumentType,
    val contentType: String,
    val linkId: UUID,
    val status: DocumentStatus,
    val isRead: Boolean = false,
    val fnr: String,
    val orgNumber: String,
    val dialogId: UUID?,
    val transmissionId: UUID? = null,
    val created: Instant,
    val updated: Instant,
)
