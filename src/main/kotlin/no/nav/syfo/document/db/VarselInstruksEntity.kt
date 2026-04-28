package no.nav.syfo.document.db

import no.nav.syfo.document.api.v1.dto.HendelseType
import java.time.Instant
import java.util.UUID

enum class VarselInstruksStatus {
    PENDING,
    PUBLISHED,
    ERROR,
}

data class VarselInstruksEntity(
    val id: Long,
    val documentId: Long,
    val type: HendelseType,
    val epostTittel: String,
    val epostBody: String,
    val smsTekst: String,
    val ressursId: String,
    val ressursUrl: String,
    val created: Instant,
    val kilde: String,
    val status: VarselInstruksStatus,
    val publishedAt: Instant?,
    val publishAttempts: Int,
    val lastPublishError: String?,
)

data class VarselInstruksPublishView(
    val id: Long,
    val documentId: UUID,
    val fnr: String,
    val orgNumber: String,
    val ressursId: String,
    val ressursUrl: String,
    val kilde: String,
    val epostTittel: String,
    val epostBody: String,
    val smsTekst: String,
)
