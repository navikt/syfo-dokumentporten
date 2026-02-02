package no.nav.syfo.document.db

import dialogEntity
import document
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import no.nav.syfo.TestDB
import no.nav.syfo.document.api.v1.dto.DocumentType
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil

class DocumentDbTest :
    DescribeSpec({
        val testDb = TestDB.database
        val documentDAO = DocumentDAO(testDb)
        val documentContentDAO = DocumentContentDAO(testDb)
        val dialogDAO = DialogDAO(testDb)
        beforeTest {
            TestDB.clearAllData()
        }

        describe("DocumentDb -> insert") {
            it("should return a generated id") {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                val documentEntity = document().toDocumentEntity(dialogEntity)
                // Act
                val id = documentDAO.insert(documentEntity, "test".toByteArray()).id
                // Assert
                id shouldNotBe null
                id shouldBeGreaterThan 0L
            }

            it("should persist the document with the correct fields") {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                val documentEntity = document().toDocumentEntity(dialogEntity)
                // Act
                val id = documentDAO.insert(documentEntity, "test".toByteArray()).id
                // Assert
                val retrievedDocument = documentDAO.getById(id)
                retrievedDocument shouldNotBe null
                retrievedDocument?.assertExpected(documentEntity, id)
            }

            it("should persist and retrieve document content correctly") {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                val documentEntity = document().toDocumentEntity(dialogEntity)
                val contentBytes = "This is a test document content.".toByteArray()
                // Act
                val id = documentDAO.insert(documentEntity, contentBytes).id
                val retrievedContent = documentContentDAO.getDocumentContentById(id)
                // Assert
                retrievedContent shouldNotBe null
                retrievedContent shouldBe contentBytes
            }
        }
        describe("DocumentDb -> update") {
            it("should return a generated id") {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                val documentEntity = documentDAO.insert(
                    document().toDocumentEntity(dialogEntity),
                    "test".toByteArray()
                )
                // Act
                val updatedDocumentEntity = documentEntity.copy(
                    status = DocumentStatus.COMPLETED,
                    isRead = true,
                    transmissionId = UUID.randomUUID(),
                    updated = Instant.now(),
                    dialog = documentEntity.dialog.copy(
                        dialogportenUUID = UUID.randomUUID(),
                        updated = Instant.now()
                    )
                )
                documentDAO.update(updatedDocumentEntity)
                val retrievedDocument = documentDAO.getById(documentEntity.id)
                // Assert
                retrievedDocument shouldNotBe null
                retrievedDocument?.assertExpected(updatedDocumentEntity, documentEntity.id)
            }
        }

        describe("DocumentDb -> getById") {
            it("should return a documentEntity for the id") {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                val documentEntity = document().toDocumentEntity(dialogEntity)
                // Act
                val id = documentDAO.insert(documentEntity, "test".toByteArray()).id
                val retrievedDocument = documentDAO.getById(id)
                // Assert
                retrievedDocument shouldNotBe null
                retrievedDocument?.assertExpected(documentEntity, id)
            }
        }

        describe("DocumentDb -> getByLinkId") {
            it("should return a documentEntity for the linkId") {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                val documentEntity = document().toDocumentEntity(dialogEntity)
                // Act
                val id = documentDAO.insert(documentEntity, "test".toByteArray()).id
                val retrievedDocument = documentDAO.getByLinkId(documentEntity.linkId)
                // Assert
                retrievedDocument shouldNotBe null
                retrievedDocument?.assertExpected(documentEntity, id)
            }
        }

        describe("Should add document to existing dialog") {
            // Arrange
            val document = document()
            dialogDAO.insertDialog(document.toDialogEntity())
            val existingDialog = dialogDAO.getByFnrAndOrgNumber(document.fnr, document.orgNumber)

            existingDialog shouldNotBe null
            existingDialog?.fnr shouldBe document.fnr
            // Act
            val persistedDocument = documentDAO.insert(
                document.toDocumentEntity(existingDialog!!),
                "test".toByteArray()
            )

            // Assert
            persistedDocument.dialog.id shouldBe existingDialog.id
        }

        describe("findDocumentsByParameters") {
            it("should return empty page when no documents exist") {
                val result = documentDAO.findDocumentsByParameters(
                    pageSize = 50,
                )

                result.items shouldBe emptyList()
                result.meta.resultSize shouldBe 0
                result.meta.hasMore shouldBe false
            }

            it("should return documents filtered by type") {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                val doc1 = document().copy(
                    type =
                    DocumentType.DIALOGMOTE
                )
                val doc2 = document().copy(
                    type =
                    DocumentType.OPPFOLGINGSPLAN
                )
                documentDAO.insert(doc1.toDocumentEntity(dialogEntity), "test".toByteArray())
                documentDAO.insert(doc2.toDocumentEntity(dialogEntity), "test".toByteArray())

                // Act
                val result = documentDAO.findDocumentsByParameters(
                    type = DocumentType.DIALOGMOTE,
                    pageSize = 50,
                )

                // Assert
                result.items.size shouldBe 1
                result.items.first().type shouldBe DocumentType.DIALOGMOTE
            }

            it("should return documents filtered by org number") {
                // Arrange
                val orgNumber1 = "123456789"
                val orgNumber2 = "987654321"
                val dialog1 = dialogDAO.insertDialog(dialogEntity().copy(orgNumber = orgNumber1))
                val dialog2 = dialogDAO.insertDialog(dialogEntity().copy(orgNumber = orgNumber2))
                documentDAO.insert(document().toDocumentEntity(dialog1), "test".toByteArray())
                documentDAO.insert(document().toDocumentEntity(dialog2), "test".toByteArray())

                // Act
                val result = documentDAO.findDocumentsByParameters(
                    orgnumber = orgNumber1,
                    pageSize = 50,
                )

                // Assert
                result.items.size shouldBe 1
                result.items.first().dialog.orgNumber shouldBe orgNumber1
            }

            it("should filter by date range") {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())

                val now = Instant.now()
                val pastDate = now.minusSeconds(3600)
                val futureDate = now.plusSeconds(3600)

                // Act
                val resultWithinRange = documentDAO.findDocumentsByParameters(
                    createdAfter = pastDate,
                    createdBefore = futureDate,
                    pageSize = 50,
                )

                val resultOutsideRange = documentDAO.findDocumentsByParameters(
                    createdAfter = futureDate,
                    pageSize = 50,
                )

                // Assert
                resultWithinRange.items.size shouldBe 1
                resultOutsideRange.items.size shouldBe 0
            }

            it("should paginate results correctly") {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                val documents = mutableListOf<PersistedDocumentEntity>()
                repeat(5) {
                    val doc = document().toDocumentEntity(dialogEntity)
                    documents.add(documentDAO.insert(doc, "test".toByteArray()))
                }

                // Act - First page has no cursor, subsequent pages use last item's created as cursor
                val page0 =
                    documentDAO.findDocumentsByParameters(pageSize = 2, orderDirection = Page.OrderDirection.ASC)
                val page1 =
                    documentDAO.findDocumentsByParameters(
                        pageSize = 2,
                        createdAfter = page0.items.last().created,
                        orderDirection = Page.OrderDirection.ASC
                    )
                val page2 =
                    documentDAO.findDocumentsByParameters(
                        pageSize = 2,
                        createdAfter = page1.items.last().created,
                        orderDirection = Page.OrderDirection.ASC
                    )

                // Assert
                ceil(page0.meta.resultSize * 1.0 / page0.meta.size) shouldBe 3
                page0.items.size shouldBe 2
                page0.meta.size shouldBe 2
                page0.meta.resultSize shouldBe 5

                page1.items.size shouldBe 2
                page2.items.size shouldBe 1
            }

            it("should respect row limit boundaries") {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())

                // Act - limit too low should be coerced to 1
                val resultLowLimit = documentDAO.findDocumentsByParameters(pageSize = 0)

                // Act - limit too high should be coerced to MAX_PAGE_SIZE
                val resultHighLimit = documentDAO.findDocumentsByParameters(pageSize = 1000)

                // Assert
                // values reflects the coerced values
                resultLowLimit.meta.pageSize shouldBe 1
                resultHighLimit.meta.pageSize shouldBe Page.MAX_PAGE_SIZE
            }

            it("should order results by created date ascending by default") {
                runTest {
                    // Arrange
                    val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                    val doc1 = documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())
                    delay(10) // Ensure different timestamps
                    val doc2 = documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())

                    // Act
                    val result = documentDAO.findDocumentsByParameters(pageSize = 50)

                    // Assert - most recent first
                    result.items.first().documentId shouldBe doc1.documentId
                    result.items.last().documentId shouldBe doc2.documentId
                }
            }

            it("should order results descending when DESC is set") {
                runTest {
                    // Arrange
                    val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                    val doc1 = documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())
                    delay(10) // Ensure different timestamps
                    val doc2 = documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())

                    // Act
                    val result = documentDAO.findDocumentsByParameters(
                        pageSize = 50,
                        orderDirection = Page.OrderDirection.DESC
                    )

                    // Assert - oldest last
                    result.items.first().documentId shouldBe doc2.documentId
                    result.items.last().documentId shouldBe doc1.documentId
                }
            }

            it("should combine multiple filters") {
                // Arrange
                val orgNumber = "555666777"
                val dialog = dialogDAO.insertDialog(dialogEntity().copy(orgNumber = orgNumber))
                val doc1 = document().copy(
                    type =
                    DocumentType.DIALOGMOTE
                )
                val doc2 = document().copy(
                    type =
                    DocumentType.OPPFOLGINGSPLAN
                )
                documentDAO.insert(doc1.toDocumentEntity(dialog), "test".toByteArray())
                documentDAO.insert(doc2.toDocumentEntity(dialog), "test".toByteArray())

                // Act
                val result = documentDAO.findDocumentsByParameters(
                    orgnumber = orgNumber,
                    type =
                    DocumentType.DIALOGMOTE,
                    pageSize = 50,
                )

                // Assert
                result.items.size shouldBe 1
                result.items.first().type shouldBe
                    DocumentType.DIALOGMOTE
                result.items.first().dialog.orgNumber shouldBe orgNumber
            }
        }
    })

fun PersistedDocumentEntity.assertExpected(expected: DocumentEntity, id: Long) {
    this.id shouldBe id
    this.documentId shouldBe expected.documentId
    this.type shouldBe expected.type
    this.contentType shouldBe expected.contentType
    this.dialog.fnr shouldBe expected.dialog.fnr
    this.dialog.orgNumber shouldBe expected.dialog.orgNumber
    this.title shouldBe expected.title
    this.summary shouldBe expected.summary
    this.linkId shouldBe expected.linkId
    this.status shouldBe expected.status
    this.isRead shouldBe expected.isRead
    this.transmissionId shouldBe expected.transmissionId
    this.updated shouldNotBe null
    this.created shouldNotBe null
    this.dialog.id shouldBe expected.dialog.id
    this.dialog.dialogportenUUID shouldBe expected.dialog.dialogportenUUID
}
