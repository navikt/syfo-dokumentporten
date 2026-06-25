package no.nav.syfo.altinn.dialogporten.client

import com.fasterxml.jackson.annotation.JsonValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import no.nav.syfo.altinn.common.AltinnTokenProvider
import no.nav.syfo.altinn.dialogporten.domain.Dialog
import no.nav.syfo.altinn.dialogporten.domain.ExtendedDialog
import no.nav.syfo.altinn.dialogporten.domain.Transmission
import no.nav.syfo.util.logger
import java.util.UUID

val JSON_PATCH_CONTENT_TYPE = ContentType(contentType = "application", contentSubtype = "json-patch+json")

interface IDialogportenClient {
    suspend fun createDialog(dialog: Dialog): UUID
    suspend fun addTransmission(transmission: Transmission, dialogId: UUID): UUID
    suspend fun patchDialog(dialogId: UUID, revisionNumber: UUID, patch: List<DialogportenClient.DialogportenPatch>)
    suspend fun getDialogById(dialogId: UUID): ExtendedDialog
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

    data class DialogportenPatch(val operation: OPERATION, val path: PATH, val value: String) {
        enum class OPERATION(val jsonValue: String) {
            REPLACE("replace"),
            ADD("add"),
            REMOVE("remove");

            @JsonValue
            fun toJson() = jsonValue
        }

        enum class PATH(val jsonValue: String) {
            IS_API_ONLY("/isApiOnly");

            @JsonValue
            fun toJson() = jsonValue
        }
    }

    override suspend fun patchDialog(dialogId: UUID, revisionNumber: UUID, patch: List<DialogportenPatch>) {
        runCatching {
            val token = altinnTokenProvider.token(AltinnTokenProvider.DIALOGPORTEN_TARGET_SCOPE).accessToken
            httpClient
                .patch("$dialogportenUrl/$dialogId") {
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    header(HttpHeaders.IfMatch, revisionNumber.toString())
                    contentType(JSON_PATCH_CONTENT_TYPE)
                    bearerAuth(token)
                    setBody(patch)
                }
        }.onFailure { e ->
            logger.error("Error on patch request to Dialogporten on dialogId: $dialogId", e)
            if (e is CancellationException) throw e
            throw DialogportenClientException(
                e.message ?: "Feil ved patch-kall til Dialogporten on dialogId: $dialogId"
            )
        }
    }

    override suspend fun getDialogById(dialogId: UUID): ExtendedDialog {
        val dialog = runCatching {
            val token = altinnTokenProvider.token(AltinnTokenProvider.DIALOGPORTEN_TARGET_SCOPE).accessToken
            httpClient
                .get("$dialogportenUrl/$dialogId") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    bearerAuth(token)
                }.body<ExtendedDialog>()
        }.getOrElse { e ->
            logger.error("Error on request to Dialogporten on dialog id: $dialogId", e)
            if (e is CancellationException) throw e
            throw DialogportenClientException(e.message ?: "Error while fetching dialog by id: $dialogId")
        }

        return dialog
    }
}
