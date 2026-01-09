package no.nav.syfo

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import no.nav.syfo.application.auth.AddTokenIssuerPlugin
import no.nav.syfo.document.api.v1.registerExternalDocumentsApiV1
import no.nav.syfo.document.api.v1.registerInternalDocumentsApiV1
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentContentDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.service.ValidationService
import no.nav.syfo.texas.TexasAzureADAuthPlugin
import no.nav.syfo.texas.client.TexasHttpClient

const val API_V1_PATH = "/api/v1"

@Suppress("LongParameterList")
fun Route.registerApiV1(
    texasHttpClient: TexasHttpClient,
    documentDAO: DocumentDAO,
    documentContentDAO: DocumentContentDAO,
    dialogDAO: DialogDAO,
    validationService: ValidationService
) {
    route("/internal$API_V1_PATH") {
        install(TexasAzureADAuthPlugin) {
            client = texasHttpClient
        }
        registerInternalDocumentsApiV1(documentDAO, dialogDAO)
    }
    route(API_V1_PATH) {
        install(AddTokenIssuerPlugin)
        registerExternalDocumentsApiV1(documentDAO, documentContentDAO, texasHttpClient, validationService)
    }

}
