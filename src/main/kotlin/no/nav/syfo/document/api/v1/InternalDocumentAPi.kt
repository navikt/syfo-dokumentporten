package no.nav.syfo.document.api.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.document.api.v1.dto.Document
import no.nav.syfo.document.service.DocumentService

fun Route.registerInternalDocumentsApiV1(documentService: DocumentService) {
    route("/documents") {
        post {
            val document = call.tryReceive<Document>()
            documentService.insertDocument(document)
            call.respond(HttpStatusCode.OK)
        }
    }
}
