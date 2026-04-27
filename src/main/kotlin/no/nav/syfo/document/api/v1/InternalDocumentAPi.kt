package no.nav.syfo.document.api.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.document.api.v1.dto.Document
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.document.api.v1.dto.validate
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.service.DialogService
import no.nav.syfo.util.logger

fun Route.registerInternalDocumentsApiV1(documentDAO: DocumentDAO, dialogService: DialogService,) {
    route("/documents") {
        post {
            val document = call.tryReceive<Document>()

            if (document.varselInstruks != null && document.type != DocumentType.DIALOGMOTE) {
                throw ApiErrorException.BadRequestException(
                    "varselInstruks er kun støttet for dokumenttype DIALOGMOTE (mottok ${document.type})"
                )
            }

            document.varselInstruks?.validate()

            runCatching {
                val existingDialog = dialogService.getAndUpdateDialogByFnrAndOrgNumber(document.fnr, document.orgNumber)
                    ?: dialogService.insertDialog(document)

                documentDAO.insert(
                    document.toDocumentEntity(existingDialog),
                    document.content,
                    document.varselInstruks,
                )

                if (document.varselInstruks != null) {
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
