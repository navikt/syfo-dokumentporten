package no.nav.syfo.altinn.dialogporten.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.syfo.altinn.common.AltinnTokenProvider
import no.nav.syfo.altinn.dialogporten.domain.Dialog
import no.nav.syfo.altinn.dialogporten.domain.Transmission
import no.nav.syfo.util.logger
import java.util.UUID

interface IDialogportenClient {
    suspend fun createDialog(dialog: Dialog): UUID
    suspend fun addTransmission(transmission: Transmission, dialogId: UUID): UUID
}

class DialogportenClient(
    baseUrl: String,
    private val httpClient: HttpClient,
    private val altinnTokenProvider: AltinnTokenProvider
) : IDialogportenClient {
    private val dialogportenUrl = "$baseUrl/dialogporten/api/v1/serviceowner/dialogs"
    private val logger = logger()

    override suspend fun createDialog(dialog: Dialog): UUID {
        val token = altinnTokenProvider.token(AltinnTokenProvider.DIALOGPORTEN_TARGET_SCOPE)
            .accessToken

        return runCatching<DialogportenClient, UUID> {
            val response =
                httpClient
                    .post(dialogportenUrl) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Accept, ContentType.Application.Json)
                        bearerAuth(token)

                        setBody(dialog)
                    }.body<String>()
            UUID.fromString(response.removeSurrounding("\""))
        }.getOrElse { e ->
            logger.error("Feil ved kall til Dialogporten for å opprette dialog", e)
            throw DialogportenClientException(e.message ?: "Feil ved kall til Dialogporten: create dialog")
        }
    }

    override suspend fun addTransmission(transmission: Transmission, dialogId: UUID): UUID {
        val token = altinnTokenProvider.token(AltinnTokenProvider.DIALOGPORTEN_TARGET_SCOPE)
            .accessToken

        return runCatching<DialogportenClient, UUID> {
            val response =
                httpClient
                    .post("$dialogportenUrl/$dialogId/transmissions") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        header(HttpHeaders.Accept, ContentType.Application.Json)
                        bearerAuth(token)

                        setBody(transmission)
                    }.body<String>()
            UUID.fromString(response.removeSurrounding("\""))
        }.getOrElse { e ->
            logger.error("Feil ved kall til Dialogporten for å opprette transmission", e)
            throw DialogportenClientException(e.message ?: "Feil ved kall til Dialogporten: add transmission")
        }
    }
}
