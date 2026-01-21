package no.nav.syfo.document.db

import dialogEntity
import document
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.syfo.TestDB
import java.time.Instant
import java.util.UUID

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
