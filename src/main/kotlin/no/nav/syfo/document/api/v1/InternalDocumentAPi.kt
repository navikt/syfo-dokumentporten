package no.nav.syfo.document.api.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.document.api.v1.dto.Document
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.util.logger

fun Route.registerInternalDocumentsApiV1(documentDAO: DocumentDAO, dialogDAO: DialogDAO) {
    route("/documents") {
        post {
            val document = call.tryReceive<Document>()
            runCatching {
                val existingDialog = dialogDAO.getByFnrAndOrgNumber(document.fnr, document.orgNumber)
                    ?: dialogDAO.insertDialog(document.toDialogEntity())
                documentDAO.insert(document.toDocumentEntity(existingDialog), document.content)
                COUNT_DOCUMENT_RECIEVED.increment()
                call.respond(HttpStatusCode.OK)
            }.onFailure {
                logger().error("Failed to insert document: ${it.message}", it)
                throw ApiErrorException.InternalServerErrorException("Failed to insert document")
            }
        }
    }
}
