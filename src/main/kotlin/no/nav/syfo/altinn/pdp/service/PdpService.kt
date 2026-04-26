package no.nav.syfo.altinn.pdp.service

import no.nav.syfo.altinn.pdp.client.Bruker
import no.nav.syfo.altinn.pdp.client.IPdpClient
import no.nav.syfo.altinn.pdp.client.harTilgang
import no.nav.syfo.util.logger
import tools.jackson.databind.ObjectMapper

class PdpService(private val pdpClient: IPdpClient,) {

    val logger = logger()
    suspend fun hasAccessToResource(bruker: Bruker, orgnrSet: Set<String>, ressurs: String): Boolean {
        logger.info("PDP access check for orgnumer $orgnrSet for ressurs $ressurs")
        val pdpResponse = pdpClient.authorize(bruker, orgnrSet, ressurs)
        return pdpResponse.harTilgang()
    }
}
