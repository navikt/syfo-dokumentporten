package no.nav.syfo.document.db

import no.nav.syfo.document.api.v1.dto.DocumentResponse
import no.nav.syfo.document.api.v1.dto.DocumentType
import java.time.Instant
import java.util.*

enum class DocumentStatus {
    RECEIVED,
    PENDING,
    COMPLETED,
    ERROR
}

open class DocumentEntity(
    open val documentId: UUID,
    open val type: DocumentType,
    open val contentType: String,
    open val title: String,
    open val summary: String?,
    open val linkId: UUID,
    open val status: DocumentStatus = DocumentStatus.RECEIVED,
    open val isRead: Boolean = false,
    open val dialog: PersistedDialogEntity,
    open val transmissionId: UUID? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DocumentEntity

        if (isRead != other.isRead) return false
        if (documentId != other.documentId) return false
        if (type != other.type) return false
        if (contentType != other.contentType) return false
        if (title != other.title) return false
        if (summary != other.summary) return false
        if (linkId != other.linkId) return false
        if (status != other.status) return false
        if (dialog != other.dialog) return false
        if (transmissionId != other.transmissionId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isRead.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (summary?.hashCode() ?: 0)
        result = 31 * result + linkId.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + dialog.hashCode()
        result = 31 * result + (transmissionId?.hashCode() ?: 0)
        return result
    }
}

data class PersistedDocumentEntity(
    val id: Long,
    override val documentId: UUID,
    override val type: DocumentType,
    override val contentType: String,
    override val title: String,
    override val summary: String?,
    override val linkId: UUID,
    override val status: DocumentStatus = DocumentStatus.RECEIVED,
    override val isRead: Boolean = false,
    override val dialog: PersistedDialogEntity,
    override val transmissionId: UUID? = null,
    val created: Instant,
    val updated: Instant,
) : DocumentEntity(
    documentId = documentId,
    type = type,
    contentType = contentType,
    title = title,
    summary = summary,
    linkId = linkId,
    status = status,
    isRead = isRead,
    dialog = dialog,
    transmissionId = transmissionId,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as PersistedDocumentEntity

        if (id != other.id) return false
        if (isRead != other.isRead) return false
        if (documentId != other.documentId) return false
        if (type != other.type) return false
        if (contentType != other.contentType) return false
        if (title != other.title) return false
        if (summary != other.summary) return false
        if (linkId != other.linkId) return false
        if (status != other.status) return false
        if (dialog != other.dialog) return false
        if (transmissionId != other.transmissionId) return false
        if (created != other.created) return false
        if (updated != other.updated) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + isRead.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (summary?.hashCode() ?: 0)
        result = 31 * result + linkId.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + dialog.hashCode()
        result = 31 * result + (transmissionId?.hashCode() ?: 0)
        result = 31 * result + created.hashCode()
        result = 31 * result + updated.hashCode()
        return result
    }

    fun toDocumentResponse(): DocumentResponse {
        return DocumentResponse(
            documentId = documentId,
            type = type,
            contentType = contentType,
            title = title,
            summary = summary,
            linkId = linkId,
            status = status,
            isRead = isRead,
            dialog = dialog.toDialogResponse(),
            transmissionId = transmissionId,
            created = created,
            updated = updated,
        )
    }
}
