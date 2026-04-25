package no.nav.syfo.altinntilganger

import no.nav.syfo.altinntilganger.client.IAltinnTilgangerClient
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.exception.UpstreamRequestException
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.util.logger

class AltinnTilgangerService(val altinnTilgangerClient: IAltinnTilgangerClient) {
    suspend fun validateTilgangToOrganisasjon(
        brukerPrincipal: BrukerPrincipal,
        orgnummers: Set<String>,
        documentType: DocumentType
    ) {
        try {
            val tilganger = altinnTilgangerClient.hentTilganger(brukerPrincipal)
            val requiredResource = requiredResourceByDocumentType[documentType]
                ?: throw ApiErrorException.InternalServerErrorException("Ukjent dokumenttype $documentType")
            val withRessursTilgang = tilganger?.tilgangTilOrgNr[requiredResource]?.toSet() ?: setOf()
            val missing = orgnummers.filterNot { it in withRessursTilgang }

            if (missing.isNotEmpty()) {
                throw ApiErrorException.ForbiddenException(
                    "Bruker har ikke tilgang til alle organisasjoner. Mangler: ${
                        missing.joinToString(
                            ", "
                        )
                    }"
                )
            }
        } catch (e: UpstreamRequestException) {
            logger.error("Feil ved henting av tilganger for personbruker", e)
            throw ApiErrorException.InternalServerErrorException("Feil ved henting av altinn-tilganger")
        }
    }

    companion object {
        private val logger = logger()
        val requiredResourceByDocumentType = mapOf(
            DocumentType.OPPFOLGINGSPLAN to "nav_syfo_oppfolgingsplan",
            DocumentType.DIALOGMOTE to "nav_syfo_dialogmote",
        )
    }
}
