package no.nav.syfo.document.service

import dialogEntity
import documentEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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

class ValidationServiceTest : DescribeSpec({
    val altinnTilgangerService = mockk<AltinnTilgangerService>()
    val eregService = mockk<EregService>()
    val pdpServiceMock = mockk<PdpService>()
    val validationService = ValidationService(altinnTilgangerService, eregService, pdpServiceMock)

    val documentEntity = documentEntity(dialogEntity())
    beforeTest {
        clearAllMocks()
        coEvery { pdpServiceMock.hasAccessToResource(any(), any(), any()) } returns true
    }

    describe("ValidationService") {
        describe("validateDocumentAccess") {
            context("when principal is BrukerPrincipal") {
                it("should validate Altinn tilgang and not throw for valid access") {
                    // Arrange
                    val brukerPrincipal = BrukerPrincipal("12345678901", "token")
                    coEvery { altinnTilgangerService.validateTilgangToOrganisasjon(any(), any(), any()) } returns Unit

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
                    coVerify(exactly = 0) {
                        eregService.getOrganization(any())
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
                    coVerify(exactly = 0) {
                        eregService.getOrganization(any())
                    }
                }


            }
        }

        describe("validateMaskinportenTilgang") {
            context("when orgnumber from token matches document orgnumber") {
                it("should allow access without checking ereg when Principal matches document orgnumber") {
                    // Arrange
                    val systemPrincipal = SystemPrincipal(
                        "0192:${documentEntity.dialog.orgNumber}",
                        "token",
                        "0192:systemOwner",
                        "systemUserId"

                    )

                    // Act & Assert - should not throw exception
                    validationService.validateMaskinportenTilgang(
                        systemPrincipal,
                        documentEntity.dialog.orgNumber,
                        documentEntity.type
                    )
                    coVerify(exactly = 0) {
                        eregService.getOrganization(any())
                    }
                    coVerify(exactly = 0) {
                        altinnTilgangerService.validateTilgangToOrganisasjon(any(), any(), any())
                    }
                }

                it("should throw ForbiddenException when PDP denies access") {
                    // Arrange
                    val systemPrincipal = SystemPrincipal(
                        "0192:${documentEntity.dialog.orgNumber}",
                        "token",
                        "0192:systemOwner",
                        "systemUserId"

                    )
                    coEvery { pdpServiceMock.hasAccessToResource(any(), any(), any()) } returns false

                    // Act & Assert - should not throw exception
                    shouldThrow<ApiErrorException.ForbiddenException> {
                        validationService.validateDocumentAccess(systemPrincipal, documentEntity)
                    }
                    coVerify(exactly = 0) {
                        eregService.getOrganization(any())
                    }
                    coVerify(exactly = 0) {
                        altinnTilgangerService.validateTilgangToOrganisasjon(any(), any(), any())
                    }
                    coVerify(exactly = 1) {
                        pdpServiceMock.hasAccessToResource(any(), any(), any())
                    }
                }
            }

            context("when orgnumber from token does not match document orgnumber") {
                context("and organization has parent organization with matching orgnumber") {
                    it("should allow access") {
                        // Arrange
                        val organization = organisasjon()
                        val entity = documentEntity.copy(
                            dialog = documentEntity.dialog.copy(
                                orgNumber = organization.organisasjonsnummer
                            ),
                        )

                        val systemPrincipal = SystemPrincipal(
                            "0192:${organization.inngaarIJuridiskEnheter!!.first().organisasjonsnummer}",
                            "token",
                            "0192:systemOwner",
                            "systemUserId"
                        )
                        coEvery { eregService.getOrganization(entity.dialog.orgNumber) } returns organization

                        // Act & Assert - should not throw exception
                        validationService.validateMaskinportenTilgang(
                            systemPrincipal,
                            entity.dialog.orgNumber,
                            entity.type
                        )

                        coVerify(exactly = 1) {
                            eregService.getOrganization(eq(entity.dialog.orgNumber))
                        }
                    }
                }

                context("and organization has no parent organizations") {
                    it("should deny access") {
                        // Arrange
                        val organization = organisasjon()
                        val entity = documentEntity.copy(
                            dialog = documentEntity.dialog.copy(
                                orgNumber = organization.organisasjonsnummer
                            ),
                        )

                        val systemPrincipal = SystemPrincipal(
                            "0192:${organization.inngaarIJuridiskEnheter!!.first().organisasjonsnummer}",
                            "token",
                            "0192:systemOwner",
                            "systemUserId"

                        )
                        coEvery { eregService.getOrganization(entity.dialog.orgNumber) } returns organization.copy(
                            inngaarIJuridiskEnheter = null
                        )

                        // Act & Assert
                        shouldThrow<ApiErrorException.ForbiddenException> {
                            validationService.validateMaskinportenTilgang(
                                systemPrincipal,
                                entity.dialog.orgNumber,
                                entity.type,
                            )
                        }
                        coVerify { eregService.getOrganization(entity.dialog.orgNumber) }
                    }
                }

                context("and organization has parent organizations but none match token orgnumber") {
                    it("should deny access") {
                        // Arrange
                        val organization = organisasjon()
                        val entity = documentEntity.copy(
                            dialog = documentEntity.dialog.copy(
                                orgNumber = organization.organisasjonsnummer
                            ),
                        )

                        val systemPrincipal = SystemPrincipal(
                            "0192:123456789",
                            "token",
                            "0192:systemOwner",
                            "systemUserId"
                        )
                        coEvery { eregService.getOrganization(entity.dialog.orgNumber) } returns organization

                        // Act & Assert
                        val exception = shouldThrow<ApiErrorException.ForbiddenException> {
                            validationService.validateMaskinportenTilgang(
                                systemPrincipal,
                                entity.dialog.orgNumber,
                                entity.type,
                            )
                        }
                        exception.message shouldBe "Access denied. Invalid organization."

                        coVerify { eregService.getOrganization(eq(entity.dialog.orgNumber)) }
                    }
                }
            }
        }
    }
})
