package no.nav.syfo.altinn.pdp.client

class FakePdpClient : IPdpClient {
    override suspend fun authorize(bruker: Bruker, orgnrSet: Set<String>, ressurs: String): PdpResponse = PdpResponse(
        response = orgnrSet.map { orgnr ->
            DecisionResult(
                decision = Decision.Permit,
                category = listOf(
                    PdpCategory(
                        id = "resource-$orgnr",
                        attribute = listOf(
                            PdpAttribute(
                                attributeId = ALTINN_RESOURCE_ATTRIBUTE_ID,
                                value = ressurs,
                            ),
                            PdpAttribute(
                                attributeId = ALTINN_ORGANIZATION_IDENTIFIER_ATTRIBUTE_ID,
                                value = orgnr,
                            ),
                        ),
                    ),
                ),
            )
        },
    )
}
