package no.nav.syfo.document.db

import no.nav.syfo.document.api.v1.dto.HendelseType
import no.nav.syfo.document.db.exposed.VarselInstruksTable
import org.jetbrains.exposed.v1.core.ResultRow
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
    val dokumentUrl: String,
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
    val dokumentUrl: String,
    val kilde: String,
    val epostTittel: String,
    val epostBody: String,
    val smsTekst: String,
)

data class VarselInstruksErrorView(
    val id: Long,
    val type: HendelseType,
    val created: Instant,
    val updated: Instant,
    val publishAttempts: Int,
    val status: VarselInstruksStatus,
)

fun ResultRow.toVarselInstruksEntity() = VarselInstruksEntity(
    id = this[VarselInstruksTable.id],
    documentId = this[VarselInstruksTable.documentId],
    type = HendelseType.valueOf(this[VarselInstruksTable.type]),
    epostTittel = this[VarselInstruksTable.epostTittel],
    epostBody = this[VarselInstruksTable.epostBody],
    smsTekst = this[VarselInstruksTable.smsTekst],
    ressursId = this[VarselInstruksTable.ressursId],
    dokumentUrl = this[VarselInstruksTable.ressursUrl],
    created = this[VarselInstruksTable.created].toInstant(),
    kilde = this[VarselInstruksTable.kilde],
    status = VarselInstruksStatus.valueOf(this[VarselInstruksTable.status]),
    publishedAt = this[VarselInstruksTable.publishedAt]?.toInstant(),
    publishAttempts = this[VarselInstruksTable.publishAttempts],
    lastPublishError = this[VarselInstruksTable.lastPublishError],
)

fun ResultRow.toVarselInstruksErrorView() = VarselInstruksErrorView(
    id = this[VarselInstruksTable.id],
    type = HendelseType.valueOf(this[VarselInstruksTable.type]),
    created = this[VarselInstruksTable.created].toInstant(),
    updated = this[VarselInstruksTable.updated].toInstant(),
    status = VarselInstruksStatus.valueOf(this[VarselInstruksTable.status]),
    publishAttempts = this[VarselInstruksTable.publishAttempts],
)
