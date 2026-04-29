package no.nav.syfo.document.db

import dialogEntity
import document
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import no.nav.syfo.TestDB
import varselInstruks
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

class VarselInstruksDAOTest :
    DescribeSpec({
        val testDb = TestDB.database
        val dialogDAO = DialogDAO(testDb)
        val varselInstruksDAO = VarselInstruksDAO(testDb)
        val documentDAO = DocumentDAO(testDb)

        beforeTest {
            TestDB.clearAllData()
        }

        describe("VarselInstruksDAO") {
            it("should insert and retrieve varsel instruks by document id") {
                runTest {
                    // Arrange
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val expectedVarselInstruks = varselInstruks()
                    val expectedRessursUrl = "https://test.nav.no/api/v1/gui/documents/test-link"

                    // Act
                    val (persistedDocument, insertedVarselInstruks) = testDb.connection.use { connection ->
                        val doc = documentDAO.insert(
                            connection,
                            document().toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val inserted = varselInstruksDAO.insert(
                            connection,
                            doc.id,
                            doc.type.altinnResource!!,
                            expectedRessursUrl,
                            expectedVarselInstruks,
                        )
                        connection.commit()
                        doc to inserted
                    }
                    val retrievedVarselInstruks = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    // Assert
                    insertedVarselInstruks.id shouldBeGreaterThan 0L
                    retrievedVarselInstruks shouldNotBe null
                    retrievedVarselInstruks?.documentId shouldBe persistedDocument.id
                    retrievedVarselInstruks?.epostTittel shouldBe expectedVarselInstruks.notifikasjonInnhold.epostTittel
                    retrievedVarselInstruks?.epostBody shouldBe expectedVarselInstruks.notifikasjonInnhold.epostBody
                    retrievedVarselInstruks?.smsTekst shouldBe expectedVarselInstruks.notifikasjonInnhold.smsTekst
                    retrievedVarselInstruks?.ressursId shouldBe persistedDocument.type.altinnResource
                    retrievedVarselInstruks?.ressursUrl shouldBe expectedRessursUrl
                    retrievedVarselInstruks?.kilde shouldBe expectedVarselInstruks.kilde
                    retrievedVarselInstruks?.type shouldBe expectedVarselInstruks.type
                    retrievedVarselInstruks?.status shouldBe VarselInstruksStatus.PENDING
                    retrievedVarselInstruks?.publishedAt shouldBe null
                    retrievedVarselInstruks?.publishAttempts shouldBe 0
                    retrievedVarselInstruks?.lastPublishError shouldBe null
                    retrievedVarselInstruks?.created shouldNotBe null
                }
            }

            it("should fail on duplicate varsel instruks for the same document") {
                runTest {
                    // Arrange
                    val dialog = dialogDAO.insertDialog(dialogEntity())

                    // Act
                    val exception = shouldThrow<SQLException> {
                        testDb.connection.use { connection ->
                            val persistedDocument = documentDAO.insert(
                                connection,
                                document().toDocumentEntity(dialog),
                                "test".toByteArray(),
                            )
                            varselInstruksDAO.insert(
                                connection,
                                persistedDocument.id,
                                persistedDocument.type.altinnResource!!,
                                "https://test.nav.no/link1",
                                varselInstruks(),
                            )
                            varselInstruksDAO.insert(
                                connection,
                                persistedDocument.id,
                                persistedDocument.type.altinnResource,
                                "https://test.nav.no/link2",
                                varselInstruks(),
                            )
                        }
                    }

                    // Assert
                    exception.message?.lowercase() shouldContain "unique"
                }
            }

            it("should return pending publish view with joined fields") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val document = document().copy(
                        varselInstruks = varselInstruks(
                            epostTittel = "Eposttittel",
                            epostBody = "Epostbody",
                            smsTekst = "Smstekst",
                        )
                    )
                    val expectedRessursUrl = "https://test.nav.no/api/v1/gui/documents/test-link"
                    val persistedDocument = testDb.connection.use { connection ->
                        val doc = documentDAO.insert(
                            connection,
                            document.toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        varselInstruksDAO.insert(
                            connection,
                            doc.id,
                            expectedRessursUrl,
                            document.varselInstruks!!,
                        )
                        connection.commit()
                        doc
                    }
                    val expectedVarselInstruks = document.varselInstruks!!
                    setVarselCreatedAt(
                        testDb = testDb,
                        documentId = persistedDocument.id,
                        created = Instant.now().minus(2, ChronoUnit.MINUTES),
                    )

                    val pending = varselInstruksDAO.getPendingForPublish(limit = 10)
                    val pendingVarselInstruks = pending.single()

                    pending shouldBe listOf(
                        VarselInstruksPublishView(
                            id = pendingVarselInstruks.id,
                            documentId = persistedDocument.documentId,
                            fnr = dialog.fnr,
                            orgNumber = dialog.orgNumber,
                            ressursId = persistedDocument.type.altinnResource!!,
                            dokumentUrl = expectedRessursUrl,
                            kilde = expectedVarselInstruks.kilde,
                            epostTittel = expectedVarselInstruks.notifikasjonInnhold.epostTittel,
                            epostBody = expectedVarselInstruks.notifikasjonInnhold.epostBody,
                            smsTekst = expectedVarselInstruks.notifikasjonInnhold.smsTekst,
                        ),
                    )
                }
            }

            it("should not return newly created varsel instruks as pending for publish") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val document = document(varselInstruks = varselInstruks())
                    testDb.connection.use { connection ->
                        val doc = documentDAO.insert(
                            connection,
                            document.toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        varselInstruksDAO.insert(
                            connection,
                            doc.id,
                            "https://test.nav.no/api/v1/gui/documents/${doc.linkId}",
                            document.varselInstruks!!,
                        )
                        connection.commit()
                    }

                    val pending = varselInstruksDAO.getPendingForPublish(limit = 10)

                    pending shouldBe emptyList()
                }
            }

            it("should mark varsel instruks as published") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val (persistedDocument, insertedVarselInstruks) = testDb.connection.use { connection ->
                        val doc = documentDAO.insert(
                            connection,
                            document().toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val inserted = varselInstruksDAO.insert(
                            connection,
                            doc.id,
                            doc.type.altinnResource!!,
                            varselInstruks(),
                        )
                        connection.commit()
                        doc to inserted
                    }
                    val publishedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)

                    testDb.connection.use { connection ->
                        varselInstruksDAO.markPublished(connection, insertedVarselInstruks.id, publishedAt)
                        connection.commit()
                    }

                    val updated = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    updated?.status shouldBe VarselInstruksStatus.PUBLISHED
                    updated?.publishedAt shouldBe publishedAt
                    updated?.publishAttempts shouldBe 1
                }
            }

            it("should keep varsel instruks pending on infra error") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val (persistedDocument, insertedVarselInstruks) = testDb.connection.use { connection ->
                        val doc = documentDAO.insert(
                            connection,
                            document().toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val inserted = varselInstruksDAO.insert(
                            connection,
                            doc.id,
                            doc.type.altinnResource!!,
                            varselInstruks(),
                        )
                        connection.commit()
                        doc to inserted
                    }

                    repeat(10) {
                        testDb.connection.use { connection ->
                            varselInstruksDAO.markPublishError(
                                connection = connection,
                                id = insertedVarselInstruks.id,
                                error = "Infrafeil",
                                isInfraError = true,
                            )
                            connection.commit()
                        }
                    }

                    val updated = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    updated?.status shouldBe VarselInstruksStatus.PENDING
                    updated?.publishAttempts shouldBe 10
                    updated?.lastPublishError shouldBe "Infrafeil"
                }
            }

            it("should mark varsel instruks as error after 10 non-infra errors") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val (persistedDocument, insertedVarselInstruks) = testDb.connection.use { connection ->
                        val doc = documentDAO.insert(
                            connection,
                            document().toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val inserted = varselInstruksDAO.insert(
                            connection,
                            doc.id,
                            doc.type.altinnResource!!,
                            varselInstruks(),
                        )
                        connection.commit()
                        doc to inserted
                    }

                    repeat(10) {
                        testDb.connection.use { connection ->
                            varselInstruksDAO.markPublishError(
                                connection = connection,
                                id = insertedVarselInstruks.id,
                                error = "Valideringsfeil",
                                isInfraError = false,
                            )
                            connection.commit()
                        }
                    }

                    val updated = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    updated?.status shouldBe VarselInstruksStatus.ERROR
                    updated?.publishAttempts shouldBe 10
                    updated?.lastPublishError shouldBe "Valideringsfeil"
                }
            }
        }
    })

private fun setVarselCreatedAt(
    testDb: no.nav.syfo.application.database.DatabaseInterface,
    documentId: Long,
    created: Instant,
) {
    testDb.connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE varsel_instruks
            SET created = ?
            WHERE document_id = ?
            """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setTimestamp(1, Timestamp.from(created))
            preparedStatement.setLong(2, documentId)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}
