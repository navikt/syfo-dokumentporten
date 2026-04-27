package no.nav.syfo.altinn.pdp.client

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

class PdpResponseTest :
    DescribeSpec({
        describe("decisionByOrgnr") {
            it("maps each decision result back to the matching orgnummer") {
                val response = PdpResponse(
                    response = listOf(
                        decisionResult(orgnr = "111111111", decision = Decision.Permit),
                        decisionResult(orgnr = "222222222", decision = Decision.Deny),
                    ),
                )

                response.decisionByOrgnr() shouldContainExactly mapOf(
                    "111111111" to Decision.Permit,
                    "222222222" to Decision.Deny,
                )
            }
        }

        describe("harTilgang") {
            it("returns false when a decision is missing for one of the requested orgnumre") {
                val response = PdpResponse(
                    response = listOf(
                        decisionResult(orgnr = "111111111", decision = Decision.Permit),
                    ),
                )

                response.harTilgang(setOf("111111111", "222222222")) shouldBe false
            }

            it("returns true when all requested orgnumre have permit decisions") {
                val response = PdpResponse(
                    response = listOf(
                        decisionResult(orgnr = "111111111", decision = Decision.Permit),
                        decisionResult(orgnr = "222222222", decision = Decision.Permit),
                    ),
                )

                response.harTilgang(setOf("111111111", "222222222")) shouldBe true
            }
        }
    })

private fun decisionResult(orgnr: String, decision: Decision) = DecisionResult(
    decision = decision,
    category = listOf(
        PdpCategory(
            id = "resource-$orgnr",
            attribute = listOf(
                PdpAttribute(
                    attributeId = "urn:altinn:organization:identifier-no",
                    value = orgnr,
                ),
            ),
        ),
    ),
)
