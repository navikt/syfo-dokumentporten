package no.nav.syfo.altinn.pdp.client

class FakePdpClient : IPdpClient {
    override suspend fun authorize(bruker: Bruker, orgnrSet: Set<String>, ressurs: String): PdpResponse = PdpResponse(
        response = listOf(
            DecisionResult(Decision.Permit)
        )
    )
}
