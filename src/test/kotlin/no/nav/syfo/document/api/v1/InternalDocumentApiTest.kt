package no.nav.syfo.document.api.v1

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import createMockToken
import defaultMocks
import document
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
import no.nav.syfo.application.api.installContentNegotiation
import no.nav.syfo.application.api.installStatusPages
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentContentDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.service.DocumentService
import no.nav.syfo.document.service.ValidationService
import no.nav.syfo.registerApiV1
import no.nav.syfo.texas.client.TexasClient
import varselInstruks

class InternalDocumentApiTest :
    DescribeSpec({
        val texasClientMock = mockk<TexasClient>()
        val documentDAOMock = mockk<DocumentDAO>()
        val dialogDAOMock = mockk<DialogDAO>()
        val documentContentDAOMock = mockk<DocumentContentDAO>()
        val documentServiceMock = mockk<DocumentService>()
        beforeTest {
            clearAllMocks()
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
                            documentService = documentServiceMock,
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
                    coEvery { documentServiceMock.insertDocument(any()) } returns Unit
                    texasClientMock.defaultMocks()
                    val document = document()

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(document)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    coVerify(exactly = 1) {
                        documentServiceMock.insertDocument(any())
                    }
                }
            }

            it("should return 400 on invalid") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"invalid": "payload"}""")
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                    coVerify(exactly = 0) {
                        documentServiceMock.insertDocument(any())
                    }
                }
            }
            it("should return 500 on db write error") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    coEvery { documentServiceMock.insertDocument(any()) } throws
                        ApiErrorException.InternalServerErrorException("Failed to insert document")

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(document())
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.InternalServerError
                    response.body<String>() shouldNotContain "DB error"
                    coVerify(exactly = 1) {
                        documentServiceMock.insertDocument(any())
                    }
                }
            }

            it("should return 200 OK and call service when varsel instruks is present") {
                withTestApplication {
                    // Arrange
                    coEvery { documentServiceMock.insertDocument(any()) } returns Unit
                    texasClientMock.defaultMocks()
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
                        documentServiceMock.insertDocument(any())
                    }
                }
            }

            it("should return 400 when varselInstruks epostTittel is blank") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    coEvery { documentServiceMock.insertDocument(any()) } throws
                        ApiErrorException.BadRequestException(
                            "varselInstruks.notifikasjonInnhold.epostTittel må være satt"
                        )
                    val doc = document(varselInstruks = varselInstruks(epostTittel = ""))

                    // Act
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }

            it("should return 400 when varselInstruks epostBody is blank") {
                withTestApplication {
                    texasClientMock.defaultMocks()
                    coEvery { documentServiceMock.insertDocument(any()) } throws
                        ApiErrorException.BadRequestException(
                            "varselInstruks.notifikasjonInnhold.epostBody må være satt"
                        )
                    val doc = document(varselInstruks = varselInstruks(epostBody = ""))
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }

            it("should return 400 when varselInstruks smsTekst is blank") {
                withTestApplication {
                    texasClientMock.defaultMocks()
                    coEvery { documentServiceMock.insertDocument(any()) } throws
                        ApiErrorException.BadRequestException(
                            "varselInstruks.notifikasjonInnhold.smsTekst må være satt"
                        )
                    val doc = document(varselInstruks = varselInstruks(smsTekst = ""))
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }

            it("should return 400 when varselInstruks epostTittel exceeds max length") {
                withTestApplication {
                    texasClientMock.defaultMocks()
                    coEvery { documentServiceMock.insertDocument(any()) } throws
                        ApiErrorException.BadRequestException(
                            "varselInstruks.notifikasjonInnhold.epostTittel kan ikke være lengre enn 255 tegn"
                        )
                    val doc = document(varselInstruks = varselInstruks(epostTittel = "x".repeat(255 + 1)))
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }

            it("should return 400 when varselInstruks epostBody exceeds max length") {
                withTestApplication {
                    texasClientMock.defaultMocks()
                    coEvery { documentServiceMock.insertDocument(any()) } throws
                        ApiErrorException.BadRequestException(
                            "varselInstruks.notifikasjonInnhold.epostBody kan ikke være lengre enn 4000 tegn"
                        )
                    val doc = document(varselInstruks = varselInstruks(epostBody = "x".repeat(4000 + 1)))
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }

            it("should return 400 when varselInstruks smsTekst exceeds max length") {
                withTestApplication {
                    texasClientMock.defaultMocks()
                    coEvery { documentServiceMock.insertDocument(any()) } throws
                        ApiErrorException.BadRequestException(
                            "varselInstruks.notifikasjonInnhold.smsTekst kan ikke være lengre enn 500 tegn"
                        )
                    val doc = document(varselInstruks = varselInstruks(smsTekst = "x".repeat(500 + 1)))
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }

            it("should return 400 when kilde is blank") {
                withTestApplication {
                    texasClientMock.defaultMocks()
                    coEvery { documentServiceMock.insertDocument(any()) } throws
                        ApiErrorException.BadRequestException("varselInstruks.kilde må være satt")
                    val doc = document(varselInstruks = varselInstruks(kilde = ""))
                    val response = client.post("/internal/api/v1/documents") {
                        contentType(ContentType.Application.Json)
                        setBody(doc)
                        bearerAuth(createMockToken(ident = "", issuer = "https://test.azuread.microsoft.com"))
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }

            it("should return 400 when varselInstruks is set for non-DIALOGMOTE document type") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    coEvery { documentServiceMock.insertDocument(any()) } throws
                        ApiErrorException.BadRequestException(
                            "varselInstruks er kun støttet for dokumenttype DIALOGMOTE"
                        )
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
