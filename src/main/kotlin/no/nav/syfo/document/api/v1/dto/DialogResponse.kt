package no.nav.syfo.document.api.v1.dto

import java.time.Instant
import java.util.UUID

data class DialogResponse(
    val title: String,
    val summary: String?,
    val orgNumber: String,
    val dialogportenUUID: UUID? = null,
    val created: Instant,
    val updated: Instant,
)
