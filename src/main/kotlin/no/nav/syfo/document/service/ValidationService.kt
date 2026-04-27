package no.nav.syfo.document.service

import no.nav.syfo.altinn.pdp.client.System
import no.nav.syfo.altinn.pdp.service.PdpService
import no.nav.syfo.altinntilganger.AltinnTilgangerService
import no.nav.syfo.altinntilganger.AltinnTilgangerService.Companion.requiredResourceByDocumentType
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.auth.Principal
import no.nav.syfo.application.auth.SystemPrincipal
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.document.db.DocumentEntity
import no.nav.syfo.util.logger

class ValidationService(
    private val altinnTilgangerService: AltinnTilgangerService,
    private val pdpService: PdpService
) {
    companion object {
        val logger = logger()
    }

    suspend fun validateDocumentAccess(principal: Principal, documentEntity: DocumentEntity) {
        validateDocumentsOfTypeAccess(principal, documentEntity.dialog.orgNumber, documentEntity.type)
    }

    suspend fun validateDocumentsOfTypeAccess(
        principal: Principal,
        requestedOrgNumber: String,
        documentType: DocumentType,
    ) {
        when (principal) {
            is BrukerPrincipal -> altinnTilgangerService.validateTilgangToOrganisasjon(
                brukerPrincipal = principal,
                orgnummer = requestedOrgNumber,
                documentType = documentType,
            )

            is SystemPrincipal -> validateAltinnRessursTilgang(
                principal = principal,
                requestedOrgNumber = requestedOrgNumber,
                documentType = documentType
            )
        }
    }

    private suspend fun validateAltinnRessursTilgang(
        principal: SystemPrincipal,
        requestedOrgNumber: String,
        documentType: DocumentType,
    ) {
        val requiredRessurs = requiredResourceByDocumentType[documentType]
            ?: throw ApiErrorException.InternalServerErrorException(
                "Could not find resource for document type $documentType"
            )

        val hasAccess = pdpService.hasAccessToResource(
            System(principal.systemUserId),
            setOf(requestedOrgNumber),
            requiredRessurs
        )
        if (!hasAccess) {
            throw ApiErrorException.ForbiddenException(
                "Access denied to resource $requiredRessurs, for system user ${principal.systemUserId}",
            )
        }
    }
}
