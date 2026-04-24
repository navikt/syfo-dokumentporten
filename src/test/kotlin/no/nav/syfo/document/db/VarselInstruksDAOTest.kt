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

class VarselInstruksDAOTest :
    DescribeSpec({
        val testDb = TestDB.database
        val dialogDAO = DialogDAO(testDb)
        val varselInstruksDAO = VarselInstruksDAO(testDb)
        val documentDAO = DocumentDAO(testDb, varselInstruksDAO)

        beforeTest {
            TestDB.clearAllData()
        }

        describe("VarselInstruksDAO") {
            it("should insert and retrieve varsel instruks by document id") {
                runTest {
                    // Arrange
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val persistedDocument = documentDAO.insert(
                        document().toDocumentEntity(dialog),
                        "test".toByteArray(),
                    )
                    val expectedVarselInstruks = varselInstruks()

                    // Act
                    val insertedVarselInstruks = testDb.connection.use { connection ->
                        val inserted = varselInstruksDAO.insert(
                            connection,
                            persistedDocument.id,
                            persistedDocument.type.altinnResource,
                            expectedVarselInstruks
                        )
                        connection.commit()
                        inserted
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
                    retrievedVarselInstruks?.ressursUrl shouldBe expectedVarselInstruks.ressursUrl
                    retrievedVarselInstruks?.kilde shouldBe expectedVarselInstruks.kilde
                    retrievedVarselInstruks?.type shouldBe expectedVarselInstruks.type.name
                    retrievedVarselInstruks?.created shouldNotBe null
                }
            }

            it("should fail on duplicate varsel instruks for the same document") {
                runTest {
                    // Arrange
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val persistedDocument = documentDAO.insert(
                        document().toDocumentEntity(dialog),
                        "test".toByteArray(),
                    )

                    // Act
                    val exception = shouldThrow<SQLException> {
                        testDb.connection.use { connection ->
                            varselInstruksDAO.insert(
                                connection,
                                persistedDocument.id,
                                persistedDocument.type.altinnResource,
                                varselInstruks()
                            )
                            varselInstruksDAO.insert(
                                connection,
                                persistedDocument.id,
                                persistedDocument.type.altinnResource,
                                varselInstruks(),
                            )
                        }
                    }

                    // Assert
                    exception.message?.lowercase() shouldContain "unique"
                }
            }
        }
    })
