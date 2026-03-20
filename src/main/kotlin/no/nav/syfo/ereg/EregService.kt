package no.nav.syfo.ereg

import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.exception.UpstreamRequestException
import no.nav.syfo.application.valkey.EregCache
import no.nav.syfo.ereg.client.IEaregClient
import no.nav.syfo.ereg.client.Organisasjon

class EregService(private val eregClient: IEaregClient, private val eregCache: EregCache) {
    suspend fun getOrganization(orgNumber: String): Organisasjon {
        eregCache.getOrganisasjon(orgNumber).let { cacheOrganisasjon ->
            if (cacheOrganisasjon != null) {
                return cacheOrganisasjon
            }
        }
        return try {
            val organisasjon = eregClient.getOrganisasjon(orgnummer = orgNumber)?.also {
                eregCache.putOrganisasjon(orgNumber, it)
            }
            organisasjon
        } catch (e: UpstreamRequestException) {
            throw ApiErrorException.InternalServerErrorException(
                "Could not get organization",
                e
            )
        }
            ?: throw ApiErrorException.BadRequestException("Unable to look up the organization")
    }
}
