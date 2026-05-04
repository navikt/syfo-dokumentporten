package no.nav.syfo.document.service

import dialogEntity
import documentEntity
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import no.nav.syfo.altinn.pdp.client.System
import no.nav.syfo.altinn.pdp.service.PdpService
import no.nav.syfo.altinntilganger.AltinnTilgangerService
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.auth.SystemPrincipal
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.ereg.EregService
import no.nav.syfo.ereg.client.Organisasjon

class ValidationServiceTest :
    DescribeSpec({
        val altinnTilgangerService = mockk<AltinnTilgangerService>()
        val eregService = mockk<EregService>()
        val pdpService = mockk<PdpService>()
        val validationService = ValidationService(altinnTilgangerService, pdpService, eregService)

        val persistedDocument = documentEntity(dialogEntity())

        fun systemPrincipal(systemUserOrgNumber: String, systemUserId: String = "system-user-id",) = SystemPrincipal(
            ident = "0192:$systemUserOrgNumber",
            token = "token",
            systemOwner = "0192:999999999",
            systemUserId = systemUserId,
        )

        // slot captures the last matching invocation — safe here because expectedOrgnrSet
        // uniquely identifies which PDP call is being verified in tests with multiple calls.
        fun verifyPdpCall(
            expectedSystemUserId: String,
            expectedOrgnrSet: Set<String>,
            expectedRessurs: String,
            exactly: Int = 1,
        ) {
            val brukerSlot = slot<System>()
            coVerify(exactly = exactly) {
                pdpService.hasAccessToResource(
                    bruker = capture(brukerSlot),
                    orgnrSet = expectedOrgnrSet,
                    ressurs = expectedRessurs,
                )
            }
            brukerSlot.captured.id shouldBe expectedSystemUserId
            brukerSlot.captured.attributeId shouldBe "urn:altinn:systemuser:uuid"
        }

        beforeTest {
            clearAllMocks()
            coEvery { pdpService.hasAccessToResource(any(), any(), any()) } returns true
        }

        describe("ValidationService") {
            describe("validateDocumentAccess") {
                context("when principal is BrukerPrincipal") {
                    it("should validate Altinn tilgang with document values") {
                        val brukerPrincipal = BrukerPrincipal("12345678901", "token")
                        coEvery {
                            altinnTilgangerService.validateTilgangToOrganisasjon(
                                brukerPrincipal = brukerPrincipal,
                                orgnummer = persistedDocument.dialog.orgNumber,
                                documentType = persistedDocument.type,
                            )
                        } returns Unit

                        validationService.validateDocumentAccess(brukerPrincipal, persistedDocument)

                        coVerify(exactly = 1) {
                            altinnTilgangerService.validateTilgangToOrganisasjon(
                                brukerPrincipal = brukerPrincipal,
                                orgnummer = persistedDocument.dialog.orgNumber,
                                documentType = persistedDocument.type,
                            )
                        }
                        coVerify(exactly = 0) { pdpService.hasAccessToResource(any(), any(), any()) }
                        coVerify(exactly = 0) { eregService.getOrganization(any()) }
                    }

                    it("should pass through forbidden exception from AltinnTilgangerService") {
                        val brukerPrincipal = BrukerPrincipal("12345678901", "token")
                        coEvery {
                            altinnTilgangerService.validateTilgangToOrganisasjon(
                                brukerPrincipal = brukerPrincipal,
                                orgnummer = persistedDocument.dialog.orgNumber,
                                documentType = persistedDocument.type,
                            )
                        } throws ApiErrorException.ForbiddenException("No access")

                        shouldThrow<ApiErrorException.ForbiddenException> {
                            validationService.validateDocumentAccess(brukerPrincipal, persistedDocument)
                        }

                        coVerify(exactly = 1) {
                            altinnTilgangerService.validateTilgangToOrganisasjon(
                                brukerPrincipal = brukerPrincipal,
                                orgnummer = persistedDocument.dialog.orgNumber,
                                documentType = persistedDocument.type,
                            )
                        }
                        coVerify(exactly = 0) { pdpService.hasAccessToResource(any(), any(), any()) }
                        coVerify(exactly = 0) { eregService.getOrganization(any()) }
                    }
                }

                context("when principal is SystemPrincipal") {
                    it("should allow access when PDP grants direct access to requested orgnumber") {
                        val requestedOrgNumber = persistedDocument.dialog.orgNumber
                        val principal = systemPrincipal(systemUserOrgNumber = "111111111")

                        validationService.validateDocumentsOfTypeAccess(
                            principal = principal,
                            requestedOrgNumber = requestedOrgNumber,
                            documentType = DocumentType.DIALOGMOTE,
                        )

                        verifyPdpCall(principal.systemUserId, setOf(requestedOrgNumber), "nav_syfo_dialogmote")
                        coVerify(exactly = 0) { eregService.getOrganization(any()) }
                        coVerify(exactly = 0) {
                            altinnTilgangerService.validateTilgangToOrganisasjon(any(), any(), any())
                        }
                    }

                    it("should use oppfolgingsplan resource when checking PDP access") {
                        val requestedOrgNumber = "123456789"
                        val principal = systemPrincipal(systemUserOrgNumber = "111111111")

                        validationService.validateDocumentsOfTypeAccess(
                            principal = principal,
                            requestedOrgNumber = requestedOrgNumber,
                            documentType = DocumentType.OPPFOLGINGSPLAN,
                        )

                        verifyPdpCall(principal.systemUserId, setOf(requestedOrgNumber), "nav_syfo_oppfolgingsplan")
                        coVerify(exactly = 0) { eregService.getOrganization(any()) }
                    }

                    it("should extract orgNumber and type from documentEntity for SystemPrincipal") {
                        val principal = systemPrincipal(
                            systemUserOrgNumber = persistedDocument.dialog.orgNumber,
                        )

                        validationService.validateDocumentAccess(principal, persistedDocument)

                        coVerify(exactly = 1) {
                            pdpService.hasAccessToResource(
                                bruker = any(),
                                orgnrSet = setOf(persistedDocument.dialog.orgNumber),
                                ressurs = "nav_syfo_dialogmote",
                            )
                        }
                        coVerify(exactly = 0) { eregService.getOrganization(any()) }
                    }

                    context("when PDP denies direct access") {
                        it("should allow access through principal orgnumber in ereg hierarchy") {
                            val requestedOrgNumber = "123456789"
                            val principalOrgNumber = "987654321"
                            val principal = systemPrincipal(systemUserOrgNumber = principalOrgNumber)
                            val organisasjon = Organisasjon(
                                organisasjonsnummer = requestedOrgNumber,
                                inngaarIJuridiskEnheter = listOf(
                                    Organisasjon(organisasjonsnummer = principalOrgNumber)
                                )
                            )
                            coEvery {
                                pdpService.hasAccessToResource(
                                    bruker = any(),
                                    orgnrSet = setOf(requestedOrgNumber),
                                    ressurs = "nav_syfo_dialogmote",
                                )
                            } returns false
                            coEvery { eregService.getOrganization(requestedOrgNumber) } returns organisasjon
                            coEvery {
                                pdpService.hasAccessToResource(
                                    bruker = any(),
                                    orgnrSet = setOf(principalOrgNumber),
                                    ressurs = "nav_syfo_dialogmote",
                                )
                            } returns true

                            shouldNotThrow<Exception> {
                                validationService.validateDocumentsOfTypeAccess(
                                    principal = principal,
                                    requestedOrgNumber = requestedOrgNumber,
                                    documentType = DocumentType.DIALOGMOTE,
                                )
                            }

                            verifyPdpCall(principal.systemUserId, setOf(requestedOrgNumber), "nav_syfo_dialogmote")
                            coVerify(exactly = 1) { eregService.getOrganization(requestedOrgNumber) }
                            verifyPdpCall(principal.systemUserId, setOf(principalOrgNumber), "nav_syfo_dialogmote")
                            coVerify(exactly = 2) { pdpService.hasAccessToResource(any(), any(), any()) }
                        }

                        it(
                            "should throw ForbiddenException when hierarchy matches but PDP denies principal orgnumber"
                        ) {
                            val requestedOrgNumber = "123456789"
                            val principalOrgNumber = "987654321"
                            val principal = systemPrincipal(systemUserOrgNumber = principalOrgNumber)
                            val organisasjon = Organisasjon(
                                organisasjonsnummer = requestedOrgNumber,
                                inngaarIJuridiskEnheter = listOf(
                                    Organisasjon(organisasjonsnummer = principalOrgNumber)
                                )
                            )
                            coEvery {
                                pdpService.hasAccessToResource(
                                    bruker = any(),
                                    orgnrSet = setOf(requestedOrgNumber),
                                    ressurs = "nav_syfo_dialogmote",
                                )
                            } returns false
                            coEvery { eregService.getOrganization(requestedOrgNumber) } returns organisasjon
                            coEvery {
                                pdpService.hasAccessToResource(
                                    bruker = any(),
                                    orgnrSet = setOf(principalOrgNumber),
                                    ressurs = "nav_syfo_dialogmote",
                                )
                            } returns false

                            shouldThrow<ApiErrorException.ForbiddenException> {
                                validationService.validateDocumentsOfTypeAccess(
                                    principal = principal,
                                    requestedOrgNumber = requestedOrgNumber,
                                    documentType = DocumentType.DIALOGMOTE,
                                )
                            }

                            verifyPdpCall(principal.systemUserId, setOf(requestedOrgNumber), "nav_syfo_dialogmote")
                            coVerify(exactly = 1) { eregService.getOrganization(requestedOrgNumber) }
                            verifyPdpCall(principal.systemUserId, setOf(principalOrgNumber), "nav_syfo_dialogmote")
                            coVerify(exactly = 2) { pdpService.hasAccessToResource(any(), any(), any()) }
                        }

                        it("should throw ForbiddenException when principal orgnumber is not in ereg hierarchy") {
                            val requestedOrgNumber = "123456789"
                            val principal = systemPrincipal(systemUserOrgNumber = "987654321")
                            val organisasjon = Organisasjon(
                                organisasjonsnummer = requestedOrgNumber,
                                inngaarIJuridiskEnheter = listOf(
                                    Organisasjon(organisasjonsnummer = "222222222")
                                )
                            )
                            coEvery {
                                pdpService.hasAccessToResource(
                                    bruker = any(),
                                    orgnrSet = setOf(requestedOrgNumber),
                                    ressurs = "nav_syfo_dialogmote",
                                )
                            } returns false
                            coEvery { eregService.getOrganization(requestedOrgNumber) } returns organisasjon

                            shouldThrow<ApiErrorException.ForbiddenException> {
                                validationService.validateDocumentsOfTypeAccess(
                                    principal = principal,
                                    requestedOrgNumber = requestedOrgNumber,
                                    documentType = DocumentType.DIALOGMOTE,
                                )
                            }

                            verifyPdpCall(principal.systemUserId, setOf(requestedOrgNumber), "nav_syfo_dialogmote")
                            coVerify(exactly = 1) { eregService.getOrganization(requestedOrgNumber) }
                            coVerify(exactly = 1) { pdpService.hasAccessToResource(any(), any(), any()) }
                        }
                    }

                    it("should throw InternalServerErrorException for unsupported document type") {
                        val principal = systemPrincipal(systemUserOrgNumber = "111111111")

                        shouldThrow<ApiErrorException.InternalServerErrorException> {
                            validationService.validateDocumentsOfTypeAccess(
                                principal = principal,
                                requestedOrgNumber = "123456789",
                                documentType = DocumentType.UNDEFINED,
                            )
                        }

                        coVerify(exactly = 0) { pdpService.hasAccessToResource(any(), any(), any()) }
                        coVerify(exactly = 0) { eregService.getOrganization(any()) }
                    }
                }
            }
        }
    })
