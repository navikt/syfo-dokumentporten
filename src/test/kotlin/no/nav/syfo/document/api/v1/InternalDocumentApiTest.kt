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
import no.nav.syfo.application.LocalEnvironment
import no.nav.syfo.application.api.installContentNegotiation
import no.nav.syfo.application.api.installStatusPages
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentContentDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.DocumentEntity
import no.nav.syfo.document.service.ValidationService
import no.nav.syfo.registerApiV1
import no.nav.syfo.texas.client.TexasClient

class InternalDocumentApiTest :
    DescribeSpec({
        val texasClientMock = mockk<TexasClient>()
        val documentDAOMock = mockk<DocumentDAO>()
        val dialogDAOMock = mockk<DialogDAO>()
        val documentContentDAOMock = mockk<DocumentContentDAO>()

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
                            env = LocalEnvironment()
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
                    coEvery { dialogDAOMock.getByFnrAndOrgNumber(any(), any()) } returns dialogEntity()
                    coEvery { documentDAOMock.insert(capture(capturedSlot), capture(capturedContent)) } returns
                        documentEntity(dialogEntity())
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
                    // Verify that the document was inserted into the database
                    coVerify(exactly = 1) {
                        documentDAOMock.insert(any(), any())
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
                    coEvery { dialogDAOMock.getByFnrAndOrgNumber(any(), any()) } returns dialogEntity()
                    coEvery { documentDAOMock.insert(any(), any()) } returns documentEntity(dialogEntity())
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
                        documentDAOMock.insert(any(), any())
                    }
                }
            }
            it("should return 500 on db write error") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks()
                    coEvery { dialogDAOMock.getByFnrAndOrgNumber(any(), any()) } returns dialogEntity()
                    coEvery { documentDAOMock.insert(any(), any()) } throws RuntimeException("DB error")
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
                        documentDAOMock.insert(any(), any())
                    }
                }
            }
        }
    })
