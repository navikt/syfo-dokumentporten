package no.nav.syfo.document.db

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

open class DialogEntity(
    open val title: String,
    open val summary: String?,
    open val fnr: String,
    open val orgNumber: String,
    open val dialogportenUUID: UUID? = null,
    open val birthDate: LocalDate? = null,
)

data class PersistedDialogEntity(
    val id: Long,
    override val title: String,
    override val summary: String?,
    override val fnr: String,
    override val orgNumber: String,
    override val dialogportenUUID: UUID? = null,
    val created: Instant,
    val updated: Instant,
    override val birthDate: LocalDate? = null,
) : DialogEntity(
    title = title,
    summary = summary,
    fnr = fnr,
    orgNumber = orgNumber,
    dialogportenUUID = dialogportenUUID,
)
