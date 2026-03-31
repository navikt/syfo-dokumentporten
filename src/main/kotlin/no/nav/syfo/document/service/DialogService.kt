package no.nav.syfo.document.service

import no.nav.syfo.document.api.v1.dto.Document
import no.nav.syfo.document.api.v1.generateDialogTitle
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.PersistedDialogEntity
import no.nav.syfo.pdl.PdlService
import java.time.LocalDate

class DialogService(private val dialogDAO: DialogDAO, private val pdlService: PdlService,) {

    suspend fun getAndUpdateDialogByFnrAndOrgNumber(fnr: String, orgNumber: String): PersistedDialogEntity? {
        val dialog = dialogDAO.getByFnrAndOrgNumber(fnr, orgNumber)
        if (dialog != null && dialog.birthDate == null) {
            val personInfo = pdlService.getPersonInfo(fnr)
            val birthDate = personInfo.birthDate?.let { LocalDate.parse(it) }
            if (birthDate != null) {
                val nameOrFnr = personInfo.fullName ?: fnr
                val newTitle = generateDialogTitle(nameOrFnr, fnr, birthDate)
                return dialogDAO.updateDialogWithBirthDate(dialog.id, birthDate, newTitle)
            }
        }
        return dialog
    }

    suspend fun insertDialog(document: Document): PersistedDialogEntity {
        val personInfo = pdlService.getPersonInfo(document.fnr)
        val enrichedDocument = document.copy(
            fullName = personInfo.fullName ?: document.fullName,
            birthDate = personInfo.birthDate?.let { LocalDate.parse(it) } ?: document.birthDate,
        )

        return dialogDAO.insertDialog(enrichedDocument.toDialogEntity())
    }
}
