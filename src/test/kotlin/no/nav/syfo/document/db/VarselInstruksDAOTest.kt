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
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import varselInstruks
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit

class VarselInstruksDAOTest :
    DescribeSpec({
        val testDb = TestDB.database
        val exposedDb = TestDB.exposedDatabase
        val dialogDAO = DialogDAO(testDb)
        val varselInstruksDAO = VarselInstruksDAO(exposedDb)
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
                    val (persistedDocument, insertedVarselInstruks) = suspendTransaction(db = exposedDb) {
                        val doc = documentDAO.insert(
                            connection.connection as Connection,
                            document().toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val inserted = varselInstruksDAO.insert(
                            doc.id,
                            doc.type.altinnResource!!,
                            expectedRessursUrl,
                            expectedVarselInstruks,
                        )
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
                    retrievedVarselInstruks?.dokumentUrl shouldBe expectedRessursUrl
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
                        suspendTransaction(db = exposedDb) {
                            val persistedDocument = documentDAO.insert(
                                connection.connection as Connection,
                                document().toDocumentEntity(dialog),
                                "test".toByteArray(),
                            )
                            varselInstruksDAO.insert(
                                persistedDocument.id,
                                persistedDocument.type.altinnResource!!,
                                "https://test.nav.no/link1",
                                varselInstruks(),
                            )
                            varselInstruksDAO.insert(
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
                    val persistedDocument = suspendTransaction(db = exposedDb) {
                        val doc = documentDAO.insert(
                            connection.connection as Connection,
                            document.toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val storedRessursId = "custom-ressurs-id"
                        varselInstruksDAO.insert(
                            doc.id,
                            storedRessursId,
                            expectedRessursUrl,
                            document.varselInstruks!!,
                        )
                        doc
                    }
                    val expectedVarselInstruks = document.varselInstruks!!
                    val pending = varselInstruksDAO.getPendingForPublish(
                        limit = 10,
                        pendingBefore = Instant.now().plusSeconds(1),
                    )
                    val pendingVarselInstruks = pending.single()

                    pending shouldBe listOf(
                        VarselInstruksPublishView(
                            id = pendingVarselInstruks.id,
                            documentId = persistedDocument.documentId,
                            fnr = dialog.fnr,
                            orgNumber = dialog.orgNumber,
                            ressursId = "custom-ressurs-id",
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
                    suspendTransaction(db = exposedDb) {
                        val doc = documentDAO.insert(
                            connection.connection as Connection,
                            document.toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        varselInstruksDAO.insert(
                            doc.id,
                            doc.type.altinnResource!!,
                            "https://test.nav.no/api/v1/gui/documents/${doc.linkId}",
                            document.varselInstruks!!,
                        )
                    }

                    val pending = varselInstruksDAO.getPendingForPublish(limit = 10)

                    pending shouldBe emptyList()
                }
            }

            it("should mark varsel instruks as published") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val (persistedDocument, insertedVarselInstruks) = suspendTransaction(db = exposedDb) {
                        val doc = documentDAO.insert(
                            connection.connection as Connection,
                            document().toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val inserted = varselInstruksDAO.insert(
                            doc.id,
                            doc.type.altinnResource!!,
                            "https://test.nav.no/api/v1/gui/documents/${doc.linkId}",
                            varselInstruks(),
                        )
                        doc to inserted
                    }
                    val publishedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)

                    suspendTransaction(db = exposedDb) {
                        varselInstruksDAO.markPublished(insertedVarselInstruks.id, publishedAt)
                    }

                    val updated = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    updated?.status shouldBe VarselInstruksStatus.PUBLISHED
                    updated?.publishedAt shouldBe publishedAt
                    updated?.publishAttempts shouldBe 1
                }
            }

            it("should keep varsel instruks pending on transient error") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val insertedVarselInstruks = suspendTransaction(db = exposedDb) {
                        val doc = documentDAO.insert(
                            connection.connection as Connection,
                            document().toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val inserted = varselInstruksDAO.insert(
                            doc.id,
                            doc.type.altinnResource!!,
                            "https://test.nav.no/api/v1/gui/documents/${doc.linkId}",
                            varselInstruks(),
                        )
                        inserted
                    }

                    repeat(9) {
                        suspendTransaction(db = exposedDb) {
                            varselInstruksDAO.markPublishError(
                                id = insertedVarselInstruks.id,
                                error = "Transient feil",
                                isPermanentError = false,
                            )
                        }
                    }

                    val updated = varselInstruksDAO.getByDocumentId(insertedVarselInstruks.documentId)

                    updated?.status shouldBe VarselInstruksStatus.PENDING
                    updated?.publishAttempts shouldBe 9
                    updated?.lastPublishError shouldBe "Transient feil"
                }
            }

            it("should mark varsel instruks as error when transient retries are exhausted") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val (persistedDocument, insertedVarselInstruks) = suspendTransaction(db = exposedDb) {
                        val doc = documentDAO.insert(
                            connection.connection as Connection,
                            document().toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val inserted = varselInstruksDAO.insert(
                            doc.id,
                            doc.type.altinnResource!!,
                            "https://test.nav.no/api/v1/gui/documents/${doc.linkId}",
                            varselInstruks(),
                        )
                        doc to inserted
                    }

                    repeat(9) {
                        suspendTransaction(db = exposedDb) {
                            varselInstruksDAO.markPublishError(
                                id = insertedVarselInstruks.id,
                                error = "Transient feil",
                                isPermanentError = false,
                            )
                        }
                    }

                    val errorView = suspendTransaction(db = exposedDb) {
                        varselInstruksDAO.markPublishError(
                            id = insertedVarselInstruks.id,
                            error = "Siste transient feil",
                            isPermanentError = false,
                        )
                    }
                    val updated = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    errorView?.status shouldBe VarselInstruksStatus.ERROR
                    errorView?.publishAttempts shouldBe 10
                    updated?.status shouldBe VarselInstruksStatus.ERROR
                    updated?.publishAttempts shouldBe 10
                    updated?.lastPublishError shouldBe "Siste transient feil"
                }
            }

            it("should mark varsel instruks as error on first permanent error and return updated db state") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val (persistedDocument, insertedVarselInstruks) = suspendTransaction(db = exposedDb) {
                        val doc = documentDAO.insert(
                            connection.connection as Connection,
                            document().toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val inserted = varselInstruksDAO.insert(
                            doc.id,
                            doc.type.altinnResource!!,
                            "https://test.nav.no/api/v1/gui/documents/${doc.linkId}",
                            varselInstruks(),
                        )
                        doc to inserted
                    }

                    val errorView = suspendTransaction(db = exposedDb) {
                        varselInstruksDAO.markPublishError(
                            id = insertedVarselInstruks.id,
                            error = "Valideringsfeil",
                            isPermanentError = true,
                        )
                    }
                    val updated = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    errorView?.status shouldBe VarselInstruksStatus.ERROR
                    errorView?.publishAttempts shouldBe 1
                    errorView?.created shouldBe insertedVarselInstruks.created
                    errorView?.updated shouldNotBe errorView?.created
                    updated?.status shouldBe VarselInstruksStatus.ERROR
                    updated?.publishAttempts shouldBe 1
                    updated?.lastPublishError shouldBe "Valideringsfeil"
                }
            }

            it("should wait for updated grace period before retrying failed varsel instruks") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val insertedVarselInstruks = suspendTransaction(db = exposedDb) {
                        val doc = documentDAO.insert(
                            connection.connection as Connection,
                            document().toDocumentEntity(dialog),
                            "test".toByteArray(),
                        )
                        val inserted = varselInstruksDAO.insert(
                            doc.id,
                            doc.type.altinnResource!!,
                            "https://test.nav.no/api/v1/gui/documents/${doc.linkId}",
                            varselInstruks(),
                        )
                        inserted
                    }

                    suspendTransaction(db = exposedDb) {
                        varselInstruksDAO.markPublishError(
                            id = insertedVarselInstruks.id,
                            error = "Transient feil",
                            isPermanentError = false,
                        )
                    }

                    varselInstruksDAO.getPendingForPublish(limit = 10) shouldBe emptyList()

                    val pending = varselInstruksDAO.getPendingForPublish(
                        limit = 10,
                        pendingBefore = Instant.now().plusSeconds(1),
                    )

                    pending.map { it.id } shouldBe listOf(insertedVarselInstruks.id)
                }
            }
        }
    })
