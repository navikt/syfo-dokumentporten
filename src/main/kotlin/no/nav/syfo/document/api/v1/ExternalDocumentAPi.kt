package no.nav.syfo.document.api.v1

import io.ktor.server.routing.Route
import no.nav.syfo.document.db.DocumentContentDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.PersistedDocumentEntity
import no.nav.syfo.document.service.ValidationService
import no.nav.syfo.texas.client.TexasClient

fun Route.registerExternalApiV1(
    documentDAO: DocumentDAO,
    documentContentDAO: DocumentContentDAO,
    texasClient: TexasClient,
    validationService: ValidationService,
) {
    registerDocumentApiV1(
        documentDAO = documentDAO,
        documentContentDAO = documentContentDAO,
        texasClient = texasClient,
        validationService = validationService,
    )
}

internal fun Route.registerDocumentApiV1(
    documentDAO: DocumentDAO,
    documentContentDAO: DocumentContentDAO,
    texasClient: TexasClient,
    validationService: ValidationService,
    onDocumentDownloaded: suspend (PersistedDocumentEntity) -> Unit = {},
) {
    DocumentApiRoutes(
        documentDAO = documentDAO,
        documentContentDAO = documentContentDAO,
        texasClient = texasClient,
        validationService = validationService,
        onDocumentDownloaded = onDocumentDownloaded,
    ).register(this)
}
