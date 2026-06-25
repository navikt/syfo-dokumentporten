package no.nav.syfo.altinn.dialogporten.client

import no.nav.syfo.altinn.dialogporten.domain.Dialog
import no.nav.syfo.altinn.dialogporten.domain.ExtendedDialog
import no.nav.syfo.altinn.dialogporten.domain.Transmission
import no.nav.syfo.util.logger
import java.util.UUID

class FakeDialogportenClient : IDialogportenClient {

    companion object {
        val logger = logger()
    }
    override suspend fun createDialog(dialog: Dialog): UUID = UUID.randomUUID()

    override suspend fun addTransmission(transmission: Transmission, dialogId: UUID): UUID = UUID.randomUUID()

    override suspend fun patchDialog(
        dialogId: UUID,
        revisionNumber: UUID,
        patch: List<DialogportenClient.DialogportenPatch>
    ) = Unit

    override suspend fun getDialogById(dialogId: UUID): ExtendedDialog =
        throw UnsupportedOperationException("FakeDialogportenClient does not support getDialogById")
}
