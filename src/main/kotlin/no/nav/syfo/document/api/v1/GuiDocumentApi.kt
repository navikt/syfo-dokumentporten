package no.nav.syfo.document.api.v1

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.altinn.dialogporten.service.DialogportenService
import no.nav.syfo.document.db.DocumentContentDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.service.ValidationService
import no.nav.syfo.texas.MaskinportenIdportenAndTokenXAuthPlugin
import no.nav.syfo.texas.client.TexasClient
import no.nav.syfo.util.logger
import java.time.Instant

fun Route.registerGuiApiV1(
    documentDAO: DocumentDAO,
    documentContentDAO: DocumentContentDAO,
    texasClient: TexasClient,
    validationService: ValidationService,
    dialogportenService: DialogportenService,
) {
    val logger = logger("GuiDocumentApi")

    route("") {
        install(MaskinportenIdportenAndTokenXAuthPlugin) {
            client = texasClient
        }
        get("/{id}") {
            val linkId = call.parameters.extractAndValidateUUIDParameter("id")
            val principal = call.getPrincipal()
            val document = documentDAO.getByLinkId(linkId) ?: throw NotFoundException("Document not found")
            validationService.validateDocumentAccess(principal, document)
            if (document.deletePerformed != null) {
                COUNT_DOCUMENT_GONE.increment()
                throw NotFoundException("Document is no longer available")
            }
            val content = documentContentDAO.getDocumentContentById(document.id)
                ?: throw NotFoundException("Document content not found")

            if (!document.isRead) {
                documentDAO.update(document.copy(isRead = true, updated = Instant.now()))
                val dialogportenDialogId = document.dialog.dialogportenUUID
                val transmissionId = document.transmissionId
                if (dialogportenDialogId != null && transmissionId != null) {
                    try {
                        dialogportenService.markTransmissionOpened(dialogportenDialogId, transmissionId)
                    } catch (e: Exception) {
                        logger.error(
                            "Failed to mark transmission $transmissionId as opened in Dialogporten",
                            e
                        )
                    }
                }
            }

            call.response.headers.append(HttpHeaders.ContentType, document.contentType)
            call.respond<ByteArray>(content)
            countRead(logger, principal, document.isRead, document.dialog.orgNumber)
            call.response.status(HttpStatusCode.OK)
        }
    }
}
