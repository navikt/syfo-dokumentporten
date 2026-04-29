package no.nav.syfo.document.service

import dialogEntity
import documentEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.altinn.pdp.service.PdpService
import no.nav.syfo.altinntilganger.AltinnTilgangerService
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.auth.SystemPrincipal
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.ereg.EregService
import organisasjon

class ValidationServiceTest :
    DescribeSpec({
        val altinnTilgangerService = mockk<AltinnTilgangerService>()
        val eregService = mockk<EregService>()
        val pdpServiceMock = mockk<PdpService>()
        val validationService = ValidationService(altinnTilgangerService, pdpServiceMock, eregService)

        val documentEntity = documentEntity(dialogEntity())
        beforeTest {
            clearAllMocks()
            coEvery { pdpServiceMock.hasAccessToResource(any(), any(), any()) } returns true
        }

        describe("ValidationService") {
            describe("validateDocumentAccess") {
                context("when principal is BrukerPrincipal") {
                    it("should validate Altinn tilgang using altinnTilgangerService and not throw for valid access") {
                        // Arrange
                        val brukerPrincipal = BrukerPrincipal("12345678901", "token")
                        coEvery { altinnTilgangerService.validateTilgangToOrganisasjon(any(), any(), any()) } returns
                            Unit

                        // Act
                        validationService.validateDocumentAccess(brukerPrincipal, documentEntity)

                        // Assert
                        coVerify(exactly = 1) {
                            altinnTilgangerService.validateTilgangToOrganisasjon(
                                any(),
                                any(),
                                any()
                            )
                        }
                    }

                    it("should validate Altinn tilgang and pass through exception from AltinnTilgangerService") {
                        // Arrange
                        val brukerPrincipal = BrukerPrincipal("12345678901", "token")
                        coEvery {
                            altinnTilgangerService.validateTilgangToOrganisasjon(
                                any(),
                                any(),
                                any()
                            )
                        } throws ApiErrorException.ForbiddenException("No access")

                        // Act
                        shouldThrow<ApiErrorException.ForbiddenException> {
                            validationService.validateDocumentAccess(brukerPrincipal, documentEntity)
                        }
                        // Assert
                        coVerify(exactly = 1) {
                            altinnTilgangerService.validateTilgangToOrganisasjon(
                                any(),
                                any(),
                                any()
                            )
                        }
                    }
                    context("when principal is SystemPrincipal") {
                        it("should allow access without checking ereg when Principal matches document orgnumber") {
                            // Arrange
                            val systemPrincipal = SystemPrincipal(
                                "0192:${documentEntity.dialog.orgNumber}",
                                "token",
                                "0192:systemOwner",
                                "systemUserId"

                            )

                            // Act & Assert - should not throw exception
                            validationService.validateDocumentsOfTypeAccess(
                                systemPrincipal,
                                documentEntity.dialog.orgNumber,
                                documentEntity.type
                            )
                            coVerify(exactly = 0) {
                                altinnTilgangerService.validateTilgangToOrganisasjon(any(), any(), any())
                            }
                        }

                        it("should throw ForbiddenException when PDP denies access") {
                            // Arrange
                            val organisasjon = organisasjon()
                            val systemPrincipal = SystemPrincipal(
                                "0192:${documentEntity.dialog.orgNumber}",
                                "token",
                                "0192:systemOwner",
                                organisasjon.inngaarIJuridiskEnheter!!.first().organisasjonsnummer

                            )
                            coEvery { pdpServiceMock.hasAccessToResource(any(), any(), any()) } returns false
                            coEvery { eregService.getOrganization((any())) } returns organisasjon
                            // Act & Assert - should not throw exception
                            shouldThrow<ApiErrorException.ForbiddenException> {
                                validationService.validateDocumentAccess(systemPrincipal, documentEntity)
                            }
                            coVerify(exactly = 0) {
                                altinnTilgangerService.validateTilgangToOrganisasjon(any(), any(), any())
                            }
                            coVerify(exactly = 1) {
                                pdpServiceMock.hasAccessToResource(any(), any(), any())
                            }
                        }
                    }
                }
            }
        }
    })
