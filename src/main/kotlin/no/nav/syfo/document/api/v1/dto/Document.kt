package no.nav.syfo.document.api.v1.dto

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import no.nav.syfo.document.api.v1.fnrToBirthDate
import no.nav.syfo.document.db.DialogEntity
import no.nav.syfo.document.db.DocumentEntity
import no.nav.syfo.document.db.PersistedDialogEntity
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Document(
    val documentId: UUID,
    val type: DocumentType,
    val content: ByteArray,
    val contentType: String,
    val fnr: String,
    val fullName: String?,
    val orgNumber: String,
    val title: String,
    val summary: String?,
) {
    fun toDocumentEntity(dialog: PersistedDialogEntity): DocumentEntity = DocumentEntity(
        documentId = documentId,
        type = type,
        contentType = contentType,
        title = title,
        summary = summary,
        linkId = UUID.randomUUID(),
        dialog = dialog,
    )

    fun toDialogEntity(): DialogEntity {
        val nameOrFnr = fullName ?: fnr
        val birthDate = fnrToBirthDate(fnr)
        val titleEnding = if (birthDate != null) {
            "(f. ${birthDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))})"
        } else {
            if (!fullName.isNullOrEmpty()) "($fnr)" else ""
        }
        return DialogEntity(
            title = "Sykefraværsoppfølging for $nameOrFnr $titleEnding".trim(),
            summary = """
                Her finner du alle dialogmøtebrev fra Nav og oppfølgingsplaner utarbeidet av nærmeste leder for $nameOrFnr.
                Innholdet er tilgjengelig i 4 måneder fra delingsdatoen. 
            """.trimIndent(),
            fnr = fnr,
            orgNumber = orgNumber,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Document

        if (documentId != other.documentId) return false
        if (type != other.type) return false
        if (!content.contentEquals(other.content)) return false
        if (contentType != other.contentType) return false
        if (fnr != other.fnr) return false
        if (fullName != other.fullName) return false
        if (orgNumber != other.orgNumber) return false
        if (title != other.title) return false
        if (summary != other.summary) return false

        return true
    }

    override fun hashCode(): Int {
        var result = documentId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + fnr.hashCode()
        result = 31 * result + (fullName?.hashCode() ?: 0)
        result = 31 * result + orgNumber.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (summary?.hashCode() ?: 0)
        return result
    }
}

enum class DocumentType(val displayName: String) {
    DIALOGMOTE("Dialogmøte"),
    OPPFOLGINGSPLAN("Oppfølgingsplan"),

    @JsonEnumDefaultValue
    UNDEFINED("Dokument"),
}
