package no.nav.syfo.document.db

import no.nav.syfo.document.api.v1.dto.ArbeidsgiverVarselType
import java.time.Instant

data class VarselInstruksEntity(
    val id: Long,
    val documentId: Long,
    val varselType: ArbeidsgiverVarselType,
    val created: Instant,
)
