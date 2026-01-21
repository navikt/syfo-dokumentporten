package no.nav.syfo.altinntilganger.client

import getMockEngine
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.UpstreamRequestException
import no.nav.syfo.texas.client.TexasClient
import no.nav.syfo.texas.client.TexasResponse
import no.nav.syfo.util.httpClientDefault

class AltinnTilgangerClientTest :
    DescribeSpec({

        val mockTexasClient = mockk<TexasClient>()
        beforeTest {
            clearAllMocks()
        }

        describe("hentTilganger") {
            it("should return AltinnTilgangerResponse when hentTilganger responds with 200") {
                val brukerPrincipal = BrukerPrincipal("12345678901", "token")
                val getPersonResponse = """
{
  "hierarki": [
    {
      "orgnr": "987654321",
      "erSlettet": false,
      "altinn3Tilganger": [],
      "altinn2Tilganger": [],
      "underenheter": [
        {
          "orgnr": "123456789",
          "erSlettet": false,
          "altinn3Tilganger": [
            "tilgang1",
            "tilgang2"
          ],
          "altinn2Tilganger": [
            "serviceCode:serviceEdition"
          ],
          "underenheter": [],
          "navn": "Organisasjon 2",
          "organisasjonsform": "BEDR"
        }
      ],
      "navn": "Organissjon 1",
      "organisasjonsform": "ORGL"
    }
  ],
  "orgNrTilTilganger": {
    "123456789": [
      "serviceCode:serviceEdition",
      "tilgang1",
      "tilgang2"
    ]
  },
  "tilgangTilOrgNr": {
    "serviceCode:serviceEdition": [
      "123456789"
    ],
    "tilgang1": [
      "123456789"
    ],
    "tilgang2": [
      "123456789"
    ]
  },
  "error": false
}

                """.trimIndent()
                val mockEngine = getMockEngine(
                    path = "/altinn-tilganger",
                    status = HttpStatusCode.Companion.OK,
                    headers = Headers.Companion.build {
                        append("Content-Type", "application/json")
                    },
                    content = getPersonResponse,
                )
                coEvery {
                    mockTexasClient.exchangeTokenForIsAltinnTilganger(eq(brukerPrincipal.token))
                } returns TexasResponse(
                    "token",
                    111,
                    "tokenType"
                )
                val client = AltinnTilgangerClient(mockTexasClient, httpClientDefault(HttpClient(mockEngine)), "")

                val result = client.hentTilganger(brukerPrincipal)

                result?.hierarki?.firstOrNull()?.orgnr shouldBe "987654321"
            }

            it("should throw exception when getPerson responds with 4xx") {
                val brukerPrincipal = BrukerPrincipal("12345678901", "token")

                val mockEngine = getMockEngine(
                    path = "/altinn-tilganger",
                    status = HttpStatusCode.Companion.BadRequest,
                    headers = Headers.Companion.build {
                        append("Content-Type", "application/json")
                    },
                    content = "invalid request",
                )
                coEvery {
                    mockTexasClient.exchangeTokenForIsAltinnTilganger(eq(brukerPrincipal.token))
                } returns TexasResponse(
                    "token",
                    111,
                    "tokenType"
                )
                val client = AltinnTilgangerClient(mockTexasClient, httpClientDefault(HttpClient(mockEngine)), "")

                shouldThrow<UpstreamRequestException> { client.hentTilganger(brukerPrincipal) }
            }

            it("should throw exception when getPerson responds with 5xx") {
                val brukerPrincipal = BrukerPrincipal("12345678901", "token")

                val mockEngine = getMockEngine(
                    path = "/altinn-tilganger",
                    status = HttpStatusCode.Companion.ServiceUnavailable,
                    headers = Headers.Companion.build {
                        append("Content-Type", "application/json")
                    },
                    content = "invalid request",
                )
                coEvery {
                    mockTexasClient.exchangeTokenForIsAltinnTilganger(eq(brukerPrincipal.token))
                } returns TexasResponse(
                    "token",
                    111,
                    "tokenType"
                )
                val client = AltinnTilgangerClient(mockTexasClient, httpClientDefault(HttpClient(mockEngine)), "")

                shouldThrow<UpstreamRequestException> { client.hentTilganger(brukerPrincipal) }
            }
        }
    })
