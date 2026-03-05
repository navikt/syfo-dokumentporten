package no.nav.syfo.document.service

import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DialogEntity
import no.nav.syfo.document.db.PersistedDialogEntity
import no.nav.syfo.pdl.PdlService
import java.time.LocalDate

class DialogService(
    private val dialogDAO: DialogDAO,
    private val pdlService: PdlService,
) {

    suspend fun getAndUpdateDialogByFnrAndOrgNumber(fnr: String, orgNumber: String): PersistedDialogEntity? {
        val dialog = dialogDAO.getByFnrAndOrgNumber(fnr, orgNumber)
        if (dialog != null && dialog.birthDate == null) {
            val birthDate = pdlService.getBirthDateFor(fnr)
            if (birthDate != null) {
                val parsed = LocalDate.parse(birthDate)
                dialogDAO.updateDialogWithBirthDate(dialog.id, parsed)
                return dialog.copy(birthDate = parsed)
            }
        }
        return dialog
    }

    private fun DialogEntity.withBirthDate(birthDate: LocalDate): DialogEntity = DialogEntity(
        title = title,
        summary = summary,
        fnr = fnr,
        orgNumber = orgNumber,
        dialogportenUUID = dialogportenUUID,
        birthDate = birthDate,
    )

    suspend fun insertDialog(document: DialogEntity): PersistedDialogEntity {
        val birthDate = pdlService.getBirthDateFor(document.fnr)
        val documentWithBirthDate = birthDate
            ?.let { document.withBirthDate(LocalDate.parse(it)) }
            ?: document

        return dialogDAO.insertDialog(documentWithBirthDate)
    }
}
