package no.nav.syfo.altinn.pdp.client

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainExactly
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
