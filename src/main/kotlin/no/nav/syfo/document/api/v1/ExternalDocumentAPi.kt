package no.nav.syfo.document.api.v1

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.auth.SystemPrincipal
import no.nav.syfo.document.db.DocumentContentDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.DocumentEntity
import no.nav.syfo.document.db.Page
import no.nav.syfo.document.service.ValidationService
import no.nav.syfo.texas.MaskinportenIdportenAndTokenXAuthPlugin
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger
import org.slf4j.Logger
import java.time.Instant

const val DOCUMENT_API_PATH = "/documents"

fun Route.registerExternalDocumentsApiV1(
    documentDAO: DocumentDAO,
    documentContentDAO: DocumentContentDAO,
    texasHttpClient: TexasHttpClient,
    validationService: ValidationService
) {
    val logger = logger("ExternalDocumentAPi")
    route("$DOCUMENT_API_PATH/{id}") {

        install(MaskinportenIdportenAndTokenXAuthPlugin) {
            client = texasHttpClient
        }
        get() {
            val linkId = call.parameters.extractAndValidateUUIDParameter("id")
            val principal = call.getPrincipal()
            val document = documentDAO.getByLinkId(linkId) ?: throw NotFoundException("Document not found")
            validationService.validateDocumentAccess(principal, document)
            val content = documentContentDAO.getDocumentContentById(document.id)
                ?: throw NotFoundException("Document content not found")
            if (!document.isRead) {
                documentDAO.update(document.copy(isRead = true, updated = Instant.now()))
            }
            call.response.headers.append(HttpHeaders.ContentType, document.contentType)
            call.respond<ByteArray>(content)
            countRead(logger, principal, document.isRead, document.dialog.orgNumber)
            call.response.status(HttpStatusCode.OK)
        }
    }

    route(DOCUMENT_API_PATH) {
        install(MaskinportenIdportenAndTokenXAuthPlugin) {
            client = texasHttpClient
        }
        get() {
            val orgNumber =
                call.queryParameters["orgNumber"] ?: throw BadRequestException("Missing parameter: orgNumber")
            val isRead = call.queryParameters["isRead"]?.toBoolean() ?: false
            val documentType = call.queryParameters.extractDocumentTypeParameter("documentType")
            val pageSize = call.getPageSize()
            val page = call.getPage()
            val createdAfter = call.getCreatedAfter()
            val principal = call.getPrincipal()

            validationService.validateDocumentsOfTypeAccess(
                principal = principal,
                requestedOrgNumber = orgNumber,
                documentType = documentType,
            )

            call.respond<Page<DocumentEntity>>(
                documentDAO.findDocumentsByParameters(
                    orgnumber = orgNumber,
                    isRead = isRead,
                    type = documentType,
                    pageSize = pageSize ?: Page.DEFAULT_PAGE_SIZE,
                    createdAfter = createdAfter,
                    page = page ?: Page.FIRST_PAGE
                )
            )
        }
    }
}

fun countRead(
    logger: Logger,
    principal: no.nav.syfo.application.auth.Principal,
    isRead: Boolean,
    orgNumber: String,
) {
    if (isRead) {
        when (principal) {
            is BrukerPrincipal -> {
                COUNT_DOCUMENTS_REREAD_BY_EXTERNAL_IDPORTENUSER.increment()
                logger.warn("Document belonging to orgNUmber $orgNumber was idporten user")
            }

            is SystemPrincipal -> {
                COUNT_DOCUMENTS_REREAD_BY_EXTERNAL_SYSTEMUSER.increment()
                logger.warn("Document belonging to orgNUmber $orgNumber was system user with owner ${principal.systemOwner}")
            }
        }
    } else {
        when (principal) {
            is BrukerPrincipal -> COUNT_DOCUMENTS_READ_BY_EXTERNAL_IDPORTENUSER.increment()
            is SystemPrincipal -> COUNT_DOCUMENTS_READ_BY_EXTERNAL_SYSTEMUSER.increment()
        }
    }
}
