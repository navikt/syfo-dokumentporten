package no.nav.syfo.altinn.dialogporten.client

import io.ktor.http.HttpStatusCode
import no.nav.syfo.altinn.dialogporten.domain.Dialog
import no.nav.syfo.altinn.dialogporten.domain.Transmission
import no.nav.syfo.util.logger
import java.util.UUID

class FakeDialogportenClient : IDialogportenClient {

    companion object {
        val logger = logger()
    }
    override suspend fun createDialog(dialog: Dialog): UUID = UUID.randomUUID()

    override suspend fun addTransmission(transmission: Transmission, dialogId: UUID): UUID = UUID.randomUUID()

    override suspend fun deleteDialog(dialogId: UUID): HttpStatusCode {
        logger.info("Deleting dialog with id: $dialogId")
        return if (dialogId == UUID.fromString("5411e06d-36e7-46b2-984f-6c6405e57160")) {
            HttpStatusCode.NotFound
        } else {
            HttpStatusCode.NoContent
        }
    }
}
