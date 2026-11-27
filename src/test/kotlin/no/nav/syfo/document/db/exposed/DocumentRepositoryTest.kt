package no.nav.syfo.document.db.exposed

import dialogEntity
import document
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.syfo.TestDB
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.DocumentEntity
import no.nav.syfo.document.db.PersistedDocumentEntity
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class DocumentRepositoryTest :
    DescribeSpec({
        val testDb = TestDB.database
        val documentRepository = DocumentRepository(TestDB.exposedDatabase)
        val documentDAO = DocumentDAO(testDb)
        val dialogDAO = DialogDAO(testDb)

        suspend fun insertDocument(documentEntity: DocumentEntity): PersistedDocumentEntity =
            testDb.connection.use { connection ->
                val result = documentDAO.insert(connection, documentEntity, "test".toByteArray())
                connection.commit()
                result
            }

        fun setDeletePerformed(id: Long, deletePerformed: Instant) {
            testDb.connection.use { connection ->
                connection.prepareStatement("UPDATE document SET delete_performed = ? WHERE id = ?").use {
                    it.setTimestamp(1, Timestamp.from(deletePerformed))
                    it.setLong(2, id)
                    it.executeUpdate()
                }
                connection.commit()
            }
        }

        beforeTest {
            TestDB.clearAllData()
        }

        describe("softDeleteByDocumentId") {
            it("should set delete_performed on all documents with the given document id") {
                val dialog = dialogDAO.insertDialog(dialogEntity())
                val documentId = UUID.randomUUID()
                val first = insertDocument(document().copy(documentId = documentId).toDocumentEntity(dialog))
                val second = insertDocument(document().copy(documentId = documentId).toDocumentEntity(dialog))
                val other = insertDocument(document().toDocumentEntity(dialog))

                val found = documentRepository.softDeleteByDocumentId(documentId)

                found shouldBe true
                documentDAO.getById(first.id)?.deletePerformed shouldNotBe null
                documentDAO.getById(second.id)?.deletePerformed shouldNotBe null
                documentDAO.getById(other.id)?.deletePerformed shouldBe null
            }

            it("should keep the original delete_performed when already deleted") {
                val dialog = dialogDAO.insertDialog(dialogEntity())
                val persisted = insertDocument(document().toDocumentEntity(dialog))
                val originalDeletePerformed = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS)
                setDeletePerformed(persisted.id, originalDeletePerformed)

                val found = documentRepository.softDeleteByDocumentId(persisted.documentId)

                found shouldBe true
                documentDAO.getById(persisted.id)?.deletePerformed shouldBe originalDeletePerformed
            }

            it("should return false when no document has the given document id") {
                val found = documentRepository.softDeleteByDocumentId(UUID.randomUUID())

                found shouldBe false
            }
        }
    })
