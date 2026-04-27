package no.nav.syfo.altinn.pdp.service

import no.nav.syfo.altinn.pdp.client.Bruker
import no.nav.syfo.altinn.pdp.client.Decision
import no.nav.syfo.altinn.pdp.client.IPdpClient
import no.nav.syfo.altinn.pdp.client.decisionByOrgnr
import no.nav.syfo.util.logger
import tools.jackson.databind.ObjectMapper

data class PdpAccessResult(val hasAccess: Boolean, val deniedOrgNumbers: Set<String>)

class PdpService(private val pdpClient: IPdpClient) {

    val logger = logger()

    suspend fun hasAccessToResource(bruker: Bruker, orgnrSet: Set<String>, ressurs: String): PdpAccessResult {
        logger.info("PDP access check for orgnumer $orgnrSet for ressurs $ressurs")
        val pdpResponse = pdpClient.authorize(bruker, orgnrSet, ressurs)
        logger.info("PDP access check for orgnumer $orgnrSet: ${ObjectMapper().writeValueAsString(pdpResponse)}")
        val decisionByOrgnr = pdpResponse.decisionByOrgnr()
        val deniedOrgNumbers = orgnrSet.filterTo(sortedSetOf()) { orgnr ->
            decisionByOrgnr[orgnr] != Decision.Permit
        }
        return PdpAccessResult(
            hasAccess = orgnrSet.isNotEmpty() && deniedOrgNumbers.isEmpty(),
            deniedOrgNumbers = deniedOrgNumbers,
        )
    }
}
