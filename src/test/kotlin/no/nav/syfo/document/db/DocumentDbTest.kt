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
import java.util.*

class DocumentDbTest : DescribeSpec({
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
                page = 0
            )

            result.items shouldBe emptyList()
            result.totalElements shouldBe 0
            result.totalPages shouldBe 0
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
                page = 0
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
                page = 0
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
                page = 0
            )

            val resultOutsideRange = documentDAO.findDocumentsByParameters(
                createdAfter = futureDate,
                pageSize = 50,
                page = 0
            )

            // Assert
            resultWithinRange.items.size shouldBe 1
            resultOutsideRange.items.size shouldBe 0
        }

        it("should paginate results correctly") {
            // Arrange
            val dialogEntity = dialogDAO.insertDialog(dialogEntity())
            repeat(5) {
                documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())
            }

            // Act
            val page0 = documentDAO.findDocumentsByParameters(pageSize = 2, page = 0)
            val page1 = documentDAO.findDocumentsByParameters(pageSize = 2, page = 1)
            val page2 = documentDAO.findDocumentsByParameters(pageSize = 2, page = 2)

            // Assert
            page0.items.size shouldBe 2
            page0.page shouldBe 0
            page0.totalElements shouldBe 5
            page0.totalPages shouldBe 3

            page1.items.size shouldBe 2
            page1.page shouldBe 1

            page2.items.size shouldBe 1
            page2.page shouldBe 2
        }

        it("should respect row limit boundaries") {
            // Arrange
            val dialogEntity = dialogDAO.insertDialog(dialogEntity())
            documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())

            // Act - limit too low should be coerced to 1
            val resultLowLimit = documentDAO.findDocumentsByParameters(pageSize = 0, page = 0)

            // Act - limit too high should be coerced to MAX_PAGE_SIZE
            val resultHighLimit = documentDAO.findDocumentsByParameters(pageSize = 1000, page = 0)

            // Assert
            // values reflects the coerced values
            resultLowLimit.pageSize shouldBe 1
            resultHighLimit.pageSize shouldBe Page.MAX_PAGE_SIZE
        }

        it("should order results by created date descending by default") {
            runTest {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                val doc1 = documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())
                delay(10) // Ensure different timestamps
                val doc2 = documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())

                // Act
                val result = documentDAO.findDocumentsByParameters(pageSize = 50, page = 0)

                // Assert - most recent first
                result.items.first().id shouldBe doc2.id
                result.items.last().id shouldBe doc1.id
            }
        }

        it("should order results ascending when ASC is set") {
            runTest {
                // Arrange
                val dialogEntity = dialogDAO.insertDialog(dialogEntity())
                val doc1 = documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())
                delay(10) // Ensure different timestamps
                val doc2 = documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())

                // Act
                val result = documentDAO.findDocumentsByParameters(
                    pageSize = 50,
                    page = 0,
                    orderDirection = Page.OrderDirection.ASC
                )

                // Assert - oldest first
                result.items.first().id shouldBe doc1.id
                result.items.last().id shouldBe doc2.id
            }
        }

        it("should filter by document status") {
            // Arrange
            val dialogEntity = dialogDAO.insertDialog(dialogEntity())
            val doc = documentDAO.insert(document().toDocumentEntity(dialogEntity), "test".toByteArray())
            documentDAO.update(doc.copy(status = DocumentStatus.COMPLETED, updated = Instant.now()))

            // Act
            val pendingResults = documentDAO.findDocumentsByParameters(
                status = DocumentStatus.PENDING,
                pageSize = 50,
                page = 0
            )
            val completedResults = documentDAO.findDocumentsByParameters(
                status = DocumentStatus.COMPLETED,
                pageSize = 50,
                page = 0
            )

            // Assert
            pendingResults.items.size shouldBe 0
            completedResults.items.size shouldBe 1
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
                page = 0
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
