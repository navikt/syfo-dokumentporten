package no.nav.syfo.dialogporten.client

import getMockEngine
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.altinn.common.AltinnTokenProvider
import no.nav.syfo.altinn.dialogporten.client.DialogportenClient
import no.nav.syfo.altinn.dialogporten.client.DialogportenClientException
import no.nav.syfo.altinn.dialogporten.domain.Activity
import no.nav.syfo.util.httpClientDefault
import no.nav.syfo.util.jacksonMapper
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class DialogportenClientTest :
    DescribeSpec({
        it("preserves Dialogporten's HTTP status for activity failures") {
            val dialogId = UUID.randomUUID()
            val transmissionId = UUID.randomUUID()
            val altinnTokenProvider = mockk<AltinnTokenProvider>()
            coEvery {
                altinnTokenProvider.token(AltinnTokenProvider.DIALOGPORTEN_TARGET_SCOPE)
            } returns AltinnTokenProvider.AltinnToken(
                accessToken = "token",
                altinnExpiryTime = 1.seconds,
                scope = AltinnTokenProvider.DIALOGPORTEN_TARGET_SCOPE,
            )
            val httpClient = httpClientDefault(
                HttpClient(
                    getMockEngine(
                        path = "/dialogporten/api/v1/serviceowner/dialogs/$dialogId/activities",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        status = HttpStatusCode.BadRequest,
                        content = "",
                    )
                )
            )
            val client = DialogportenClient(
                baseUrl = "",
                httpClient = httpClient,
                altinnTokenProvider = altinnTokenProvider,
            )

            val exception = shouldThrow<DialogportenClientException> {
                client.createActivity(
                    Activity(
                        id = transmissionId,
                        type = Activity.ActivityType.TransmissionOpened,
                        transmissionId = transmissionId,
                        performedBy = Activity.ActivityActor(actorType = Activity.ActorType.ServiceOwner),
                    ),
                    dialogId,
                )
            }

            exception.status shouldBe HttpStatusCode.BadRequest
        }

        it("returns the created activity ID") {
            val dialogId = UUID.randomUUID()
            val activityId = UUID.randomUUID()
            val altinnTokenProvider = mockk<AltinnTokenProvider>()
            coEvery {
                altinnTokenProvider.token(AltinnTokenProvider.DIALOGPORTEN_TARGET_SCOPE)
            } returns AltinnTokenProvider.AltinnToken(
                accessToken = "token",
                altinnExpiryTime = 1.seconds,
                scope = AltinnTokenProvider.DIALOGPORTEN_TARGET_SCOPE,
            )
            val httpClient = httpClientDefault(
                HttpClient(
                    getMockEngine(
                        path = "/dialogporten/api/v1/serviceowner/dialogs/$dialogId/activities",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        status = HttpStatusCode.Created,
                        content = "\"$activityId\"",
                    )
                )
            )
            val client = DialogportenClient(
                baseUrl = "",
                httpClient = httpClient,
                altinnTokenProvider = altinnTokenProvider,
            )

            client.createActivity(
                Activity(
                    id = activityId,
                    type = Activity.ActivityType.TransmissionOpened,
                    transmissionId = activityId,
                    performedBy = Activity.ActivityActor(actorType = Activity.ActorType.ServiceOwner),
                ),
                dialogId,
            ) shouldBe activityId
        }

        it("serializes ServiceOwner actor type") {
            val activity = Activity(
                id = UUID.randomUUID(),
                type = Activity.ActivityType.TransmissionOpened,
                performedBy = Activity.ActivityActor(actorType = Activity.ActorType.ServiceOwner),
            )

            jacksonMapper().readTree(jacksonMapper().writeValueAsString(activity))
                .path("performedBy")
                .path("actorType")
                .asText() shouldBe "ServiceOwner"
        }
    })
