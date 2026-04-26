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
            is BrukerPrincipal -> validateAltTilgang(
                principal,
                setOf(documentEntity.dialog.orgNumber),
                documentEntity.type
            )

            is SystemPrincipal -> validateMaskinportenTilgang(
                principal,
                setOf(documentEntity.dialog.orgNumber),
                documentEntity.type
            )
        }
    }

    suspend fun validateDocumentsOfTypeAccess(
        principal: Principal,
        requestedOrgNumber: Set<String>,
        documentType: DocumentType,
    ) {
        when (principal) {
            is BrukerPrincipal -> altinnTilgangerService.validateTilgangToOrganisasjon(
                principal,
                requestedOrgNumber,
                documentType,
            )

            is SystemPrincipal -> validateMaskinportenTilgang(
                principal,
                requestedOrgNumber,
                documentType
            )
        }
    }

    private suspend fun validateAltTilgang(
        principal: BrukerPrincipal,
        requestedOrgNumbers: Set<String>,
        documentType: DocumentType
    ) {
        altinnTilgangerService.validateTilgangToOrganisasjon(
            principal,
            requestedOrgNumbers,
            documentType
        )
    }

    suspend fun validateMaskinportenTilgang(
        principal: SystemPrincipal,
        requestedOrgNumbers: Set<String>,
        documentType: DocumentType
    ) {
        val orgNumberFromToken = maskinportenIdToOrgnumber(principal.ident)
//        if (orgNumberFromToken != requestedOrgNumbers.firstOrNull()) {
//            validateHierarchicalEeregAccess(requestedOrgNumbers.firstOrNull() ?: "", orgNumberFromToken)
//        }
        validateAltinnRessursTilgang(principal, documentType, requestedOrgNumbers)
    }

    private suspend fun validateHierarchicalEeregAccess(requestedOrgNumber: String, orgNumberFromToken: String) {
        val organisasjon = eregService.getOrganization(requestedOrgNumber)
        if (!organisasjon.aggregerOrgnummereFraHierarki().contains(orgNumberFromToken)) {
            val errorMessage = "Orgnumber $orgNumberFromToken from SystemUser is not found in the organization " +
                "hierarchy of requested orgnumber $requestedOrgNumber"
            logger.warn(
                errorMessage,
            )
            throw ApiErrorException.ForbiddenException(errorMessage)
        }
    }

    private suspend fun validateAltinnRessursTilgang(
        principal: SystemPrincipal,
        documentType: DocumentType,
        requestedOrgNumbers: Set<String>
    ) {
        val requiredRessurs = requiredResourceByDocumentType[documentType]
            ?: throw ApiErrorException.InternalServerErrorException(
                "Could not find resource for document type $documentType"
            )

        val hasAccess = pdpService.hasAccessToResource(
            bruker = System(principal.systemUserId),
//            orgnrSet = setOf(
//                maskinportenIdToOrgnumber(principal.ident),
//                maskinportenIdToOrgnumber(principal.systemOwner)
//            ),
            orgnrSet = requestedOrgNumbers,
            ressurs = requiredRessurs
        )
        if (!hasAccess) {
            throw ApiErrorException.ForbiddenException(
                "Access denied to resource $requiredRessurs, for system user ${principal.systemUserId}",
            )
        }
    }
}
