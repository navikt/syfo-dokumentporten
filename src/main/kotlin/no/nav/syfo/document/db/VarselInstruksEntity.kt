package no.nav.syfo.document.db

import no.nav.syfo.document.api.v1.dto.HendelseType
import java.time.Instant

data class VarselInstruksEntity(
    val id: Long,
    val documentId: Long,
    val type: HendelseType,
    val epostTittel: String,
    val epostBody: String,
    val smsTekst: String,
    val ressursUrl: String,
    val created: Instant,
    val kilde: String,
)
