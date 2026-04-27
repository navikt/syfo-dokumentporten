package no.nav.syfo.document.api.v1

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import createMockToken
import defaultMocks
import dialogEntity
import document
import documentEntity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import no.nav.syfo.TestDB
import no.nav.syfo.application.api.installContentNegotiation
import no.nav.syfo.application.api.installStatusPages
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.document.api.v1.dto.VarselInstruks
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentContentDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.DocumentEntity
import no.nav.syfo.document.service.DialogService
import no.nav.syfo.document.service.ValidationService
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.registerApiV1
import no.nav.syfo.texas.client.TexasClient
import varselInstruks

class InternalDocumentApiTest :
    DescribeSpec({
        val texasClientMock = mockk<TexasClient>()
        val documentDAOMock = mockk<DocumentDAO>()
        val dialogDAOMock = mockk<DialogDAO>()
        val documentContentDAOMock = mockk<DocumentContentDAO>()
        val pdlService = mockk<PdlService>()
        beforeTest {
            clearAllMocks()
            TestDB.clearAllData()
        }
        fun withTestApplication(fn: suspend ApplicationTestBuilder.() -> Unit) {
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
                            texasClientMock,
                            documentDAOMock,
                            documentContentDAOMock,
                            dialogDAOMock,
                            validationService = mockk<ValidationService>(),
                            dialogService = DialogService(dialogDAOMock, pdlService),
                        )
                    }
                }
                fn(this)
            }
        }
        describe("POST /documents") {
            it("should return 200 OK for valid payload") {
                withTestApplication {
                    // Arrange
                    val capturedSlot = slot<DocumentEntity>()
                    val capturedContent = slot<ByteArray>()
                    val existingDialog = dialogEntity()
                    coEvery { dialogDAOMock.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { dialogDAOMock.updateDialogWithBirthDate(any(), any(), any()) } returns
                        existingDialog
                    coEvery {
                        documentDAOMock.insert(capture(capturedSlot), capture(capturedContent), any())
                    } returns
                        documentEntity(dialogEntity())
                    texasClientMock.defaultMocks()
                    coEvery { pdlService.getPersonInfo(any()) } returns
                        no.nav.syfo.pdl.PdlPersonInfo(fullName = null, birthDate = "1990-01-15")
                    val document = document()
                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(document)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    // Verify that the document was inserted into the database
                    coVerify(exactly = 1) {
                        documentDAOMock.insert(any(), any(), any())
                        capturedContent
                            .captured
                            .toString(Charsets.UTF_8) shouldBe document.content.toString(Charsets.UTF_8)
                    }
                }
            }

            it("should return 400 on invalid") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    val existingDialog = dialogEntity()
                    coEvery { dialogDAOMock.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { dialogDAOMock.updateDialogWithBirthDate(any(), any(), any()) } returns
                        existingDialog
                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"invalid": "payload"}""")
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                    // Verify that the document was inserted into the database
                    coVerify(exactly = 0) {
                        documentDAOMock.insert(any(), any(), any())
                    }
                }
            }
            it("should return 500 on db write error") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    val existingDialog = dialogEntity()
                    coEvery { dialogDAOMock.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { dialogDAOMock.updateDialogWithBirthDate(any(), any(), any()) } returns
                        existingDialog
                    coEvery { pdlService.getPersonInfo(any()) } returns
                        no.nav.syfo.pdl.PdlPersonInfo(fullName = null, birthDate = "1990-01-15")
                    coEvery { documentDAOMock.insert(any(), any(), any()) } throws RuntimeException("DB error")
                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(document())
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.InternalServerError
                    response.body<String>() shouldNotContain "DB error"
                    // Verify that the document was inserted into the database
                    coVerify(exactly = 1) {
                        documentDAOMock.insert(any(), any(), any())
                    }
                }
            }

            it("should return 200 OK and persist varsel instruks when present") {
                withTestApplication {
                    // Arrange
                    val existingDialog = dialogEntity()
                    val persistedDoc = documentEntity(existingDialog)
                    val capturedVarselInstruks = slot<VarselInstruks>()
                    coEvery { dialogDAOMock.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { dialogDAOMock.updateDialogWithBirthDate(any(), any(), any()) } returns existingDialog
                    coEvery {
                        documentDAOMock.insert(any(), any(), capture(capturedVarselInstruks))
                    } returns persistedDoc
                    texasClientMock.defaultMocks()
                    coEvery { pdlService.getPersonInfo(any()) } returns
                        no.nav.syfo.pdl.PdlPersonInfo(fullName = null, birthDate = "1990-01-15")

                    val doc = document(varselInstruks = varselInstruks())

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    coVerify(exactly = 1) {
                        documentDAOMock.insert(any(), any(), any())
                    }
                    capturedVarselInstruks.captured.notifikasjonInnhold.epostTittel shouldBe
                        doc.varselInstruks?.notifikasjonInnhold?.epostTittel
                }
            }

            it("should insert with null varselInstruks when varselInstruks is absent") {
                withTestApplication {
                    // Arrange
                    val existingDialog = dialogEntity()
                    coEvery { dialogDAOMock.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { dialogDAOMock.updateDialogWithBirthDate(any(), any(), any()) } returns existingDialog
                    coEvery { documentDAOMock.insert(any(), any(), any()) } returns documentEntity(existingDialog)
                    texasClientMock.defaultMocks()
                    coEvery { pdlService.getPersonInfo(any()) } returns
                        no.nav.syfo.pdl.PdlPersonInfo(fullName = null, birthDate = "1990-01-15")

                    val doc = document()

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    coVerify(exactly = 1) {
                        documentDAOMock.insert(any(), any(), null)
                    }
                }
            }

            it("should return 400 when varselInstruks epostTittel is blank") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    val doc = document(varselInstruks = varselInstruks(epostTittel = ""))

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                    coVerify(exactly = 0) {
                        documentDAOMock.insert(any(), any(), any())
                    }
                }
            }

            it("should return 400 when kilde is blank") {
                withTestApplication {
                    texasClientMock.defaultMocks()
                    val doc = document(varselInstruks = varselInstruks(kilde = ""))
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                    coVerify(exactly = 0) { documentDAOMock.insert(any(), any(), any()) }
                }
            }

            it("should return 400 when varselInstruks ressursUrl is invalid") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    val doc = document(varselInstruks = varselInstruks(ressursUrl = "ikke en url"))

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                    coVerify(exactly = 0) {
                        documentDAOMock.insert(any(), any(), any())
                    }
                }
            }

            it("should return 400 when varselInstruks ressursUrl uses javascript scheme") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    val doc = document(varselInstruks = varselInstruks(ressursUrl = "javascript:alert(1)"))

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                    coVerify(exactly = 0) {
                        documentDAOMock.insert(any(), any(), any())
                    }
                }
            }

            it("should return 400 when varselInstruks ressursUrl uses file scheme") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    val doc = document(varselInstruks = varselInstruks(ressursUrl = "file:///etc/passwd"))

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                    coVerify(exactly = 0) {
                        documentDAOMock.insert(any(), any(), any())
                    }
                }
            }

            it("should return 400 when varselInstruks ressursUrl is missing host") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    val doc = document(varselInstruks = varselInstruks(ressursUrl = "https://"))

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                    coVerify(exactly = 0) {
                        documentDAOMock.insert(any(), any(), any())
                    }
                }
            }

            it("should return 400 when varselInstruks is set for non-DIALOGMOTE document type") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    val doc = document(type = DocumentType.OPPFOLGINGSPLAN, varselInstruks = varselInstruks())

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                    coVerify(exactly = 0) {
                        documentDAOMock.insert(any(), any(), any())
                    }
                }
            }
        }
    })
