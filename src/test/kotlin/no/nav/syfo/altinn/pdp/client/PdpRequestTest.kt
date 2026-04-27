package no.nav.syfo.altinn.pdp.client

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PdpRequestTest :
    DescribeSpec({
        describe("lagPdpRequest") {
            it("creates multiRequests with one requestReference per orgnummer") {
                val request = lagPdpRequest(
                    bruker = Person("12345678901"),
                    orgnrSet = linkedSetOf("111111111", "222222222"),
                    ressurs = "syfo-resource",
                )

                request.request.combinedDecision shouldBe false
                request.request.accessSubject.single().id shouldBe "subject1"
                request.request.action.single().id shouldBe "action1"
                request.request.resource.map { it.id } shouldBe listOf("resource1", "resource2")
                request.request.resource.map { category ->
                    category.attribute.associate { attribute -> attribute.attributeId to attribute.value }
                } shouldBe listOf(
                    mapOf(
                        "urn:altinn:resource" to "syfo-resource",
                        "urn:altinn:organization:identifier-no" to "111111111",
                    ),
                    mapOf(
                        "urn:altinn:resource" to "syfo-resource",
                        "urn:altinn:organization:identifier-no" to "222222222",
                    ),
                )
                request.request.multiRequests?.requestReference?.map { it.referenceId } shouldBe listOf(
                    listOf("subject1", "action1", "resource1"),
                    listOf("subject1", "action1", "resource2"),
                )
            }
        }
    })
