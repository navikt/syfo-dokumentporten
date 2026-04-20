package no.nav.syfo.document.api.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.document.api.v1.dto.ArbeidsgiverVarselType
import no.nav.syfo.document.api.v1.dto.Document
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.VarselInstruksDAO
import no.nav.syfo.document.service.DialogService
import no.nav.syfo.util.logger

fun Route.registerInternalDocumentsApiV1(
    documentDAO: DocumentDAO,
    dialogService: DialogService,
    varselInstruksDAO: VarselInstruksDAO,
) {
    route("/documents") {
        post {
            val document = call.tryReceive<Document>()

            validateVarselInstruks(document)

            runCatching {
                val existingDialog = dialogService.getAndUpdateDialogByFnrAndOrgNumber(document.fnr, document.orgNumber)
                    ?: dialogService.insertDialog(document)

                val persistedDocument = documentDAO.insert(document.toDocumentEntity(existingDialog), document.content)

                if (document.varselInstruks != null) {
                    varselInstruksDAO.insert(persistedDocument.id, document.varselInstruks.varselType)
                    COUNT_VARSEL_INSTRUKS_RECEIVED.increment()
                }

                COUNT_DOCUMENT_RECIEVED.increment()
                call.respond(HttpStatusCode.OK)
            }.onFailure {
                logger().error("Failed to insert document: ${it.message}", it)
                throw ApiErrorException.InternalServerErrorException("Failed to insert document")
            }
        }
    }
}

private fun validateVarselInstruks(document: Document) {
    val instruks = document.varselInstruks ?: return

    if (document.type != DocumentType.DIALOGMOTE) {
        throw ApiErrorException.BadRequestException(
            "varselInstruks is only allowed for documents of type DIALOGMOTE, got ${document.type}"
        )
    }

    if (instruks.varselType == ArbeidsgiverVarselType.UNKNOWN) {
        throw ApiErrorException.BadRequestException(
            "Invalid varselType in varselInstruks"
        )
    }
}
