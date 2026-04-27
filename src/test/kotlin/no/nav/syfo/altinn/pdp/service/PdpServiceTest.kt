package no.nav.syfo.altinn.pdp.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.altinn.pdp.client.Decision
import no.nav.syfo.altinn.pdp.client.DecisionResult
import no.nav.syfo.altinn.pdp.client.IPdpClient
import no.nav.syfo.altinn.pdp.client.PdpAttribute
import no.nav.syfo.altinn.pdp.client.PdpCategory
import no.nav.syfo.altinn.pdp.client.PdpResponse
import no.nav.syfo.altinn.pdp.client.Person

class PdpServiceTest :
    DescribeSpec({
        describe("hasAccessToResource") {
            it("returns false when authorize does not return a decision for every requested orgnummer") {
                val pdpClient = mockk<IPdpClient>()
                coEvery {
                    pdpClient.authorize(any(), any(), any())
                } returns PdpResponse(
                    response = listOf(
                        DecisionResult(
                            decision = Decision.Permit,
                            category = listOf(
                                PdpCategory(
                                    attribute = listOf(
                                        PdpAttribute(
                                            attributeId = "urn:altinn:organization:identifier-no",
                                            value = "111111111",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )

                val service = PdpService(pdpClient)

                service.hasAccessToResource(
                    bruker = Person("12345678901"),
                    orgnrSet = setOf("111111111", "222222222"),
                    ressurs = "syfo-resource",
                ) shouldBe false
            }
        }
    })
