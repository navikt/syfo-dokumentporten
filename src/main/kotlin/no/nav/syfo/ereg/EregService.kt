package no.nav.syfo.ereg

import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.exception.UpstreamRequestException
import no.nav.syfo.ereg.client.IEaregClient
import no.nav.syfo.ereg.client.Organisasjon

class EregService(private val eregClient: IEaregClient) {
    suspend fun getOrganization(orgNumber: String): Organisasjon = try {
        eregClient.getOrganisasjon(orgnummer = orgNumber)
    } catch (e: UpstreamRequestException) {
        throw ApiErrorException.InternalServerErrorException(
            "Could not get organization",
            e
        )
    }
        ?: throw ApiErrorException.BadRequestException("Unable to look up the organization")
}
