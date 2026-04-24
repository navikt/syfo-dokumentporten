package no.nav.syfo.document.db

import java.time.Instant

data class VarselInstruksEntity(
    val id: Long,
    val documentId: Long,
    val type: String,
    val epostTittel: String,
    val epostBody: String,
    val smsTekst: String,
    val ressursId: String,
    val ressursUrl: String,
    val created: Instant,
    val kilde: String,
)
