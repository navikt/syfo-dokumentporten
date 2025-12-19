package no.nav.syfo.document.db

import no.nav.syfo.document.api.v1.dto.DialogResponse
import java.time.Instant
import java.util.*

open class DialogEntity(
    open val title: String,
    open val summary: String?,
    open val fnr: String,
    open val orgNumber: String,
    open val dialogportenUUID: UUID? = null
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
) : DialogEntity(
    title = title,
    summary = summary,
    fnr = fnr,
    orgNumber = orgNumber,
    dialogportenUUID = dialogportenUUID,
) {
    fun toDialogResponse() =
        DialogResponse(
            title = title,
            summary = summary,
            fnr = fnr,
            orgNumber = orgNumber,
            dialogportenUUID = dialogportenUUID,
            created = created,
            updated = updated,
        )
}
