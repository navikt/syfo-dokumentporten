package no.nav.syfo.document.service

import no.nav.syfo.altinn.pdp.client.System
import no.nav.syfo.altinn.pdp.service.PdpService
import no.nav.syfo.altinntilganger.AltinnTilgangerService
import no.nav.syfo.altinntilganger.AltinnTilgangerService.Companion.requiredResourceByDocumentType
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.auth.Principal
import no.nav.syfo.application.auth.SystemPrincipal
import no.nav.syfo.application.auth.maskinportenIdToOrgnumber
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.document.db.DocumentEntity
import no.nav.syfo.ereg.EregService
import no.nav.syfo.util.logger

class ValidationService(
    private val altinnTilgangerService: AltinnTilgangerService,
    private val eregService: EregService,
    private val pdpService: PdpService
) {
    companion object {
        val logger = logger()
    }

    suspend fun validateDocumentAccess(principal: Principal, documentEntity: DocumentEntity) {
        when (principal) {
            is BrukerPrincipal -> validateAltTilgang(principal, documentEntity)
            is SystemPrincipal -> validateMaskinportenTilgang(principal, documentEntity)
        }
    }

    private suspend fun validateAltTilgang(principal: BrukerPrincipal, documentEntity: DocumentEntity) {
        altinnTilgangerService.validateTilgangToOrganisasjon(
            principal,
            documentEntity.dialog!!.orgNumber,
            documentEntity.type
        )
    }

    suspend fun validateMaskinportenTilgang(principal: SystemPrincipal, documentEntity: DocumentEntity) {
        val orgNumberFromToken = maskinportenIdToOrgnumber(principal.ident)
        if (orgNumberFromToken != documentEntity.dialog!!.orgNumber) {
            val organisasjon = eregService.getOrganization(documentEntity.dialog.orgNumber)
            if (organisasjon.inngaarIJuridiskEnheter?.filter { it.organisasjonsnummer == orgNumberFromToken }
                    .isNullOrEmpty()
            ) {
                logger.warn(
                    "Maskinporten orgnummer $orgNumberFromToken does not match document orgnummer ${documentEntity.dialog.orgNumber} or any parent organization."
                )
                throw ApiErrorException.ForbiddenException("Access denied. Invalid organization.")
            }
        }
        validateAltinnRessursTilgang(principal, documentEntity.type)
    }

    private suspend fun validateAltinnRessursTilgang(principal: SystemPrincipal, documentType: DocumentType) {
        val requiredRessurs = requiredResourceByDocumentType[documentType]
            ?: throw ApiErrorException.InternalServerErrorException(
                "Could not find resource for document type $documentType"
            )

        val hasAccess = pdpService.hasAccessToResource(
            System(principal.systemUserId),
            setOf(
                maskinportenIdToOrgnumber(principal.ident),
                maskinportenIdToOrgnumber(principal.systemOwner)
            ),
            requiredRessurs
        )
        if (!hasAccess) {
            throw ApiErrorException.ForbiddenException(
                "Access denied to resource $requiredRessurs, for system user ${principal.systemUserId}",
            )
        }
    }
}
