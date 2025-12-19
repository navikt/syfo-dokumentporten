package no.nav.syfo.document.api.v1

import DefaultOrganization
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import createMockToken
import defaultMocks
import dialogEntity
import document
import documentContent
import documentEntity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import no.nav.syfo.TestDB
import no.nav.syfo.altinn.pdp.service.PdpService
import no.nav.syfo.altinntilganger.AltinnTilgangerService
import no.nav.syfo.altinntilganger.client.FakeAltinnTilgangerClient
import no.nav.syfo.application.api.installContentNegotiation
import no.nav.syfo.application.api.installStatusPages
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentContentDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.Page
import no.nav.syfo.document.db.PersistedDocumentEntity
import no.nav.syfo.document.service.ValidationService
import no.nav.syfo.ereg.EregService
import no.nav.syfo.ereg.client.FakeEregClient
import no.nav.syfo.registerApiV1
import no.nav.syfo.texas.MASKINPORTEN_ARKIVPORTEN_SCOPE
import no.nav.syfo.texas.MASKINPORTEN_SYFO_DOKUMENTPORTEN_SCOPE
import no.nav.syfo.texas.client.TexasHttpClient
import organisasjon

class ExternalDocumentApiTest : DescribeSpec({
    val texasHttpClientMock = mockk<TexasHttpClient>()
    val documentDAO = mockk<DocumentDAO>(relaxed = true)
    val documentContentDAO = mockk<DocumentContentDAO>(relaxed = true)
    val dialogDAO = mockk<DialogDAO>()
    val fakeAltinnTilgangerClient = FakeAltinnTilgangerClient()
    val fakeEregClient = FakeEregClient()
    val eregService = EregService(fakeEregClient)
    val eregServiceSpy = spyk(eregService)
    val pdpServiceMock = mockk<PdpService>()
    val validationService =
        ValidationService(AltinnTilgangerService(fakeAltinnTilgangerClient), eregServiceSpy, pdpServiceMock)
    val validationServiceSpy = spyk(validationService)
    val tokenXIssuer = "https://tokenx.nav.no"
    val idportenIssuer = "https://test.idporten.no"

    beforeTest {
        clearAllMocks()
        TestDB.clearAllData()
        coEvery { pdpServiceMock.hasAccessToResource(any(), any(), any()) } returns true
    }

    fun withTestApplication(
        fn: suspend ApplicationTestBuilder.() -> Unit
    ) {
        testApplication {
            this.client = createClient {
                install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
            }
            application {
                installContentNegotiation()
                installStatusPages()
                routing {
                    registerApiV1(
                        texasHttpClient = texasHttpClientMock,
                        documentDAO = documentDAO,
                        documentContentDAO = documentContentDAO,
                        dialogDAO = dialogDAO,
                        validationService = validationServiceSpy
                    )
                }
            }
            fn(this)
        }
    }
    describe("GET /documents") {
        describe("Maskinporten token") {
            it("should return 200 OK for authorized token") {
                withTestApplication {
                    // Arrange
                    val document = documentEntity(dialogEntity())
                    coEvery { documentDAO.getByLinkId(eq(document.linkId)) } returns document
                    coEvery { documentContentDAO.getDocumentContentById(eq(document.id)) } returns documentContent()
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(
                            ID = "0192:${document.dialog.orgNumber}"
                        ),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )
                    // Act
                    val response = client.get("api/v1/documents/${document.linkId}") {
                        bearerAuth(createMockToken(ident = document.dialog.orgNumber))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    response.headers["Content-Type"] shouldBe document.contentType
                    coVerify(exactly = 1) {
                        validationServiceSpy.validateDocumentAccess(any(), eq(document))
                    }
                    coVerify(exactly = 1) {
                        documentDAO.update(match {
                            it.isRead == true
                        })
                    }
                }
            }
            it("should return 200 OK for authorized token for the new scope") {
                withTestApplication {
                    // Arrange
                    val document = documentEntity(dialogEntity())
                    coEvery { documentDAO.getByLinkId(eq(document.linkId)) } returns document
                    coEvery { documentContentDAO.getDocumentContentById(eq(document.id)) } returns documentContent()
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(
                            ID = "0192:${document.dialog.orgNumber}"
                        ),
                        scope = MASKINPORTEN_SYFO_DOKUMENTPORTEN_SCOPE,
                    )
                    // Act
                    val response = client.get("api/v1/documents/${document.linkId}") {
                        bearerAuth(createMockToken(ident = document.dialog.orgNumber))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    response.headers["Content-Type"] shouldBe document.contentType
                    coVerify(exactly = 1) {
                        validationServiceSpy.validateDocumentAccess(any(), eq(document))
                    }
                }
            }

            it("should return 200 OK for authorized token from parent org unit") {
                withTestApplication {
                    // Arrange
                    val organization = organisasjon()
                    val document = documentEntity(dialogEntity().copy(orgNumber = organization.organisasjonsnummer))
                    coEvery { documentDAO.getByLinkId(eq(document.linkId)) } returns document
                    coEvery { documentContentDAO.getDocumentContentById(eq(document.id)) } returns documentContent()
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(
                            ID = "0192:${organization.inngaarIJuridiskEnheter!!.first().organisasjonsnummer}"
                        ),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )
                    fakeEregClient.organisasjoner[document.dialog.orgNumber] = organization
                    // Act
                    val response = client.get("api/v1/documents/${document.linkId}") {
                        bearerAuth(createMockToken(ident = organization.inngaarIJuridiskEnheter.first().organisasjonsnummer))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    response.headers["Content-Type"] shouldBe document.contentType
                    coVerify(exactly = 1) {
                        validationServiceSpy.validateDocumentAccess(any(), eq(document))
                    }
                    coVerify(exactly = 1) {
                        documentDAO.update(match {
                            it.isRead == true
                        })
                    }
                }
            }

            it("should return 200 OK for authorized token from parent org unit with new name") {
                withTestApplication {
                    // Arrange
                    val organization = organisasjon()
                    val document = documentEntity(dialogEntity().copy(orgNumber = organization.organisasjonsnummer))
                    coEvery { documentDAO.getByLinkId(eq(document.linkId)) } returns document
                    coEvery { documentContentDAO.getDocumentContentById(eq(document.id)) } returns documentContent()
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(
                            ID = "0192:${organization.inngaarIJuridiskEnheter!!.first().organisasjonsnummer}"
                        ),
                        scope = MASKINPORTEN_SYFO_DOKUMENTPORTEN_SCOPE,
                    )
                    fakeEregClient.organisasjoner[document.dialog.orgNumber] = organization
                    // Act
                    val response = client.get("api/v1/documents/${document.linkId}") {
                        bearerAuth(createMockToken(ident = organization.inngaarIJuridiskEnheter.first().organisasjonsnummer))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    response.headers["Content-Type"] shouldBe document.contentType
                    coVerify(exactly = 1) {
                        validationServiceSpy.validateDocumentAccess(any(), eq(document))
                    }
                }
            }

            it("should return 403 Forbidden for unauthorized token") {
                withTestApplication {
                    // Arrange
                    val nonMatchingOrgNumber = "999999999"
                    val organization = organisasjon()
                    val document = documentEntity(dialogEntity().copy(orgNumber = organization.organisasjonsnummer))
                    coEvery { documentDAO.getByLinkId(eq(document.linkId)) } returns document
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(
                            ID = "0192:$nonMatchingOrgNumber" // Different orgnumber
                        ),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )
                    fakeEregClient.organisasjoner[document.dialog.orgNumber] = organization
                    // Act
                    val response = client.get("api/v1/documents/${document.linkId}") {
                        bearerAuth(createMockToken(ident = nonMatchingOrgNumber))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.Forbidden
                    coVerify(exactly = 1) {
                        validationServiceSpy.validateDocumentAccess(any(), eq(document))
                    }
                    coVerify(exactly = 0) {
                        documentDAO.update(any())
                    }
                }
            }
        }

        describe("TokenX token") {
            it("should return 200 OK for authorized token") {
                withTestApplication {
                    // Arrange
                    val document = documentEntity(dialogEntity())
                    val callerPid = "11223344556"
                    texasHttpClientMock.defaultMocks(
                        acr = "Level4",
                        pid = callerPid
                    )
                    fakeAltinnTilgangerClient.usersWithAccess.add(callerPid to document.dialog.orgNumber)
                    coEvery { documentDAO.getByLinkId(eq(document.linkId)) } returns document
                    coEvery { documentContentDAO.getDocumentContentById(eq(document.id)) } returns documentContent()
                    // Act
                    val response = client.get("api/v1/documents/${document.linkId}") {
                        bearerAuth(createMockToken(callerPid, issuer = tokenXIssuer))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    response.headers["Content-Type"] shouldBe document.contentType
                    coVerify(exactly = 1) {
                        validationServiceSpy.validateDocumentAccess(any(), eq(document))
                    }
                }
            }

            it("should return 403 Forbidden if token lacks Level4") {
                withTestApplication {
                    // Arrange
                    val document = documentEntity(dialogEntity())
                    val callerPid = "11223344556"
                    texasHttpClientMock.defaultMocks(
                        acr = "Level3",
                        pid = callerPid
                    )
                    fakeAltinnTilgangerClient.usersWithAccess.add(callerPid to document.dialog.orgNumber)
                    coEvery { documentDAO.getByLinkId(eq(document.linkId)) } returns document
                    // Act
                    val response = client.get("api/v1/documents/${document.linkId}") {
                        bearerAuth(createMockToken(callerPid, issuer = tokenXIssuer))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.Forbidden
                    coVerify(exactly = 0) {
                        validationServiceSpy.validateDocumentAccess(any(), eq(document))
                    }
                }
            }

            it("should return 403 Forbidden when token user lacks altinn resource") {
                withTestApplication {
                    // Arrange
                    val document = documentEntity(dialogEntity())
                    val callerPid = "11223344556"
                    texasHttpClientMock.defaultMocks(
                        acr = "Level4",
                        pid = callerPid
                    )
                    fakeAltinnTilgangerClient.usersWithAccess.clear()
                    coEvery { documentDAO.getByLinkId(eq(document.linkId)) } returns document
                    // Act
                    val response = client.get("api/v1/documents/${document.linkId}") {
                        bearerAuth(createMockToken(callerPid, issuer = tokenXIssuer))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.Forbidden
                    coVerify(exactly = 1) {
                        validationServiceSpy.validateDocumentAccess(any(), eq(document))
                    }
                }
            }
        }

        describe("Idporten token") {
            it("should return 200 OK for authorized token") {
                withTestApplication {
                    // Arrange
                    val document = documentEntity(dialogEntity())
                    val callerPid = "11223344556"
                    texasHttpClientMock.defaultMocks(
                        acr = "Level4",
                        pid = callerPid
                    )
                    fakeAltinnTilgangerClient.usersWithAccess.add(callerPid to document.dialog.orgNumber)
                    coEvery { documentDAO.getByLinkId(eq(document.linkId)) } returns document
                    coEvery { documentContentDAO.getDocumentContentById(eq(document.id)) } returns documentContent()
                    // Act
                    val response = client.get("api/v1/documents/${document.linkId}") {
                        bearerAuth(createMockToken(callerPid, issuer = idportenIssuer))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    response.headers["Content-Type"] shouldBe document.contentType
                    coVerify(exactly = 1) {
                        validationServiceSpy.validateDocumentAccess(any(), eq(document))
                    }
                }
            }

            describe("Not Found") {
                it("should return 404 Not found for unknown id") {
                    withTestApplication {
                        // Arrange
                        val document = document().toDocumentEntity(dialogEntity())
                        coEvery { documentDAO.getByLinkId(eq(document.linkId)) } returns null
                        texasHttpClientMock.defaultMocks(
                            consumer = DefaultOrganization.copy(
                                ID = "0192:${document.dialog.orgNumber}"
                            ),
                            scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                        )
                        // Act
                        val response = client.get("api/v1/documents/${document.linkId}") {
                            bearerAuth(createMockToken(ident = document.dialog.orgNumber))
                        }

                        // Assert
                        response.status shouldBe HttpStatusCode.NotFound
                        coVerify(exactly = 0) {
                            validationServiceSpy.validateDocumentAccess(any(), eq(document))
                        }
                    }
                }
            }
        }
    }

    describe("GET /documents (paginated list)") {
        describe("Maskinporten token") {
            it("should return 200 OK with paginated documents") {
                withTestApplication {
                    // Arrange
                    val orgNumber = "123456789"
                    val dialog = dialogEntity().copy(orgNumber = orgNumber)
                    val documents = listOf(
                        documentEntity(dialog),
                        documentEntity(dialog),
                        documentEntity(dialog)
                    )
                    val page = Page(
                        page = 0,
                        totalPages = 1,
                        totalElements = 3,
                        pageSize = 50,
                        items = documents
                    )
                    coEvery {
                        documentDAO.findDocumentsByParameters(any(), any())
                    } returns page
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(ID = "0192:$orgNumber"),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )

                    // Act
                    val response =
                        client.get("/api/v1/documents?organizationId=$orgNumber&documentType=DIALOGMOTE&createdAfter=2024-01-01T00:00:00Z") {
                            bearerAuth(createMockToken(ident = orgNumber))
                        }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("should return 200 OK with filtered documents by type") {
                withTestApplication {
                    // Arrange
                    val orgNumber = "123456789"
                    val dialog = dialogEntity().copy(orgNumber = orgNumber)
                    val documents = listOf(documentEntity(dialog).copy(type = DocumentType.DIALOGMOTE))
                    val page = Page(
                        page = 0,
                        totalPages = 1,
                        totalElements = 1,
                        pageSize = 50,
                        items = documents
                    )
                    coEvery {
                        documentDAO.findDocumentsByParameters(
                            any(),
                            any(),
                            type = DocumentType.DIALOGMOTE,
                        )
                    } returns page
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(ID = "0192:$orgNumber"),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )

                    // Act
                    val response =
                        client.get("/api/v1/documents?organizationId=$orgNumber&documentType=DIALOGMOTE&createdAfter=2024-01-01T00:00:00Z") {
                            bearerAuth(createMockToken(ident = orgNumber))
                        }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("should return 200 OK with custom pagination parameters") {
                withTestApplication {
                    // Arrange
                    val orgNumber = "123456789"
                    val dialog = dialogEntity().copy(orgNumber = orgNumber)
                    val documents = listOf(documentEntity(dialog))
                    val page = Page(
                        page = 2,
                        totalPages = 5,
                        totalElements = 25,
                        pageSize = 5,
                        items = documents
                    )
                    coEvery {
                        documentDAO.findDocumentsByParameters(
                            pageSize = 5,
                            page = 2
                        )
                    } returns page
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(ID = "0192:$orgNumber"),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )

                    // Act
                    val response =
                        client.get("/api/v1/documents?organizationId=$orgNumber&documentType=DIALOGMOTE&createdAfter=2024-01-01T00:00:00Z&pageSize=5&page=2") {
                            bearerAuth(createMockToken(ident = orgNumber))
                        }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("should return 200 OK with isRead filter") {
                withTestApplication {
                    // Arrange
                    val orgNumber = "123456789"
                    val page = Page<PersistedDocumentEntity>(
                        page = 0,
                        totalPages = 0,
                        totalElements = 0,
                        pageSize = 50,
                        items = emptyList()
                    )
                    coEvery {
                        documentDAO.findDocumentsByParameters(
                            any(),
                            any(),
                            isRead = true,
                        )
                    } returns page
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(ID = "0192:$orgNumber"),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )

                    // Act
                    val response =
                        client.get("/api/v1/documents?organizationId=$orgNumber&documentType=DIALOGMOTE&createdAfter=2024-01-01T00:00:00Z&isRead=true") {
                            bearerAuth(createMockToken(ident = orgNumber))
                        }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("should return 400 Bad Request when organizationId is missing") {
                withTestApplication {
                    // Arrange
                    val orgNumber = "123456789"
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(ID = "0192:$orgNumber"),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )

                    // Act
                    val response =
                        client.get("/api/v1/documents?documentType=DIALOGMOTE&createdAfter=2024-01-01T00:00:00Z") {
                            bearerAuth(createMockToken(ident = orgNumber))
                        }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }

            it("should return 400 Bad Request when documentType is missing") {
                withTestApplication {
                    // Arrange
                    val orgNumber = "123456789"
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(ID = "0192:$orgNumber"),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )

                    // Act
                    val response =
                        client.get("/api/v1/documents?organizationId=$orgNumber&createdAfter=2024-01-01T00:00:00Z") {
                            bearerAuth(createMockToken(ident = orgNumber))
                        }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }

            it("should return 400 Bad Request when createdAfter is missing") {
                withTestApplication {
                    // Arrange
                    val orgNumber = "123456789"
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(ID = "0192:$orgNumber"),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )

                    // Act
                    val response = client.get("/api/v1/documents?organizationId=$orgNumber&documentType=DIALOGMOTE") {
                        bearerAuth(createMockToken(ident = orgNumber))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }

            it("should return 403 Forbidden for unauthorized organization") {
                withTestApplication {
                    // Arrange
                    val requestedOrgNumber = "123456789"
                    val tokenOrgNumber = "987654321"
                    // Add the requested org to the fake client so it's found, but without parent relationship
                    fakeEregClient.organisasjoner[requestedOrgNumber] = organisasjon().copy(
                        organisasjonsnummer = requestedOrgNumber,
                        inngaarIJuridiskEnheter = emptyList() // No parent relationship to tokenOrgNumber
                    )
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(ID = "0192:$tokenOrgNumber"),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )

                    // Act
                    val response =
                        client.get("/api/v1/documents?organizationId=$requestedOrgNumber&documentType=DIALOGMOTE&createdAfter=2024-01-01T00:00:00Z") {
                            bearerAuth(createMockToken(ident = tokenOrgNumber))
                        }

                    // Assert
                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }

            it("should return empty page when no documents match") {
                withTestApplication {
                    // Arrange
                    val orgNumber = "123456789"
                    val emptyPage = Page<PersistedDocumentEntity>(
                        page = 0,
                        totalPages = 0,
                        totalElements = 0,
                        pageSize = 50,
                        items = emptyList()
                    )
                    coEvery {
                        documentDAO.findDocumentsByParameters(
                            any(),
                            any()
                        )
                    } returns emptyPage
                    texasHttpClientMock.defaultMocks(
                        systemBrukerOrganisasjon = DefaultOrganization.copy(ID = "0192:$orgNumber"),
                        scope = MASKINPORTEN_ARKIVPORTEN_SCOPE,
                    )

                    // Act
                    val response =
                        client.get("/api/v1/documents?organizationId=$orgNumber&documentType=DIALOGMOTE&createdAfter=2024-01-01T00:00:00Z") {
                            bearerAuth(createMockToken(ident = orgNumber))
                        }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                }
            }
        }
    }
})
