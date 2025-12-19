package no.nav.syfo.document.api.v1.dto

import no.nav.syfo.document.db.DocumentStatus
import java.time.Instant
import java.util.*

data class DocumentResponse(
    val documentId: UUID,
    val type: DocumentType,
    val contentType: String,
    val title: String,
    val summary: String?,
    val linkId: UUID,
    val status: DocumentStatus = DocumentStatus.RECEIVED,
    val isRead: Boolean = false,
    val dialog: DialogResponse,
    val transmissionId: UUID? = null,
    val created: Instant,
    val updated: Instant,
)
