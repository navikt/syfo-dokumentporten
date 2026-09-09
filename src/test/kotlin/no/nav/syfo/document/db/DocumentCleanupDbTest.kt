package no.nav.syfo.document.db

import dialogEntity
import document
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.syfo.TestDB
import java.sql.Timestamp
import java.time.Instant

class DocumentCleanupDbTest :
    DescribeSpec({
        val database = TestDB.database
        val documentDAO = DocumentDAO(database)
        val documentContentDAO = DocumentContentDAO(database)
        val dialogDAO = DialogDAO(database)

        suspend fun insertDocument(created: Instant): PersistedDocumentEntity {
            val dialog = dialogDAO.insertDialog(dialogEntity())
            val persistedDocument = database.connection.use { connection ->
                documentDAO.insert(connection, document().toDocumentEntity(dialog), "content".toByteArray()).also {
                    connection.commit()
                }
            }
            database.connection.use { connection ->
                connection.prepareStatement("UPDATE document SET created = ? WHERE id = ?").use { statement ->
                    statement.setTimestamp(1, Timestamp.from(created))
                    statement.setLong(2, persistedDocument.id)
                    statement.executeUpdate()
                }
                connection.commit()
            }
            return persistedDocument
        }

        fun deleteContent(documentId: Long) {
            database.connection.use { connection ->
                connection.prepareStatement("DELETE FROM document_content WHERE id = ?").use { statement ->
                    statement.setLong(1, documentId)
                    statement.executeUpdate()
                }
                connection.commit()
            }
        }

        fun softDelete(documentId: Long, deletePerformed: Instant) {
            database.connection.use { connection ->
                connection.prepareStatement("UPDATE document SET delete_performed = ? WHERE id = ?").use { statement ->
                    statement.setTimestamp(1, Timestamp.from(deletePerformed))
                    statement.setLong(2, documentId)
                    statement.executeUpdate()
                }
                connection.commit()
            }
        }

        fun contentDeletedAt(documentId: Long): Instant? = database.connection.use { connection ->
            connection.prepareStatement("SELECT content_deleted_at FROM document WHERE id = ?").use { statement ->
                statement.setLong(1, documentId)
                statement.executeQuery().use { resultSet ->
                    check(resultSet.next())
                    resultSet.getTimestamp("content_deleted_at")?.toInstant()
                }
            }
        }

        beforeTest {
            TestDB.clearAllData()
        }

        it("cleans only documents strictly older than the cutoff") {
            val cutoff = Instant.parse("2026-04-30T12:00:00Z")
            val oldDocument = insertDocument(cutoff.minusSeconds(1))
            val boundaryDocument = insertDocument(cutoff)
            val recentDocument = insertDocument(cutoff.plusSeconds(1))

            val result = documentDAO.cleanupExpiredDocuments(cutoff, 500)

            result shouldBe DocumentCleanupBatchResult(processedCount = 1, deletedContentCount = 1)
            documentDAO.getById(oldDocument.id)?.deletePerformed shouldNotBe null
            documentContentDAO.getDocumentContentById(oldDocument.id) shouldBe null
            documentDAO.getById(boundaryDocument.id)?.deletePerformed shouldBe null
            documentContentDAO.getDocumentContentById(boundaryDocument.id) shouldNotBe null
            documentDAO.getById(recentDocument.id)?.deletePerformed shouldBe null
            documentContentDAO.getDocumentContentById(recentDocument.id) shouldNotBe null
        }

        it("treats missing content as success and cleans content for an already soft-deleted document") {
            val cutoff = Instant.parse("2026-04-30T12:00:00Z")
            val missingContentDocument = insertDocument(cutoff.minusSeconds(2))
            val softDeletedDocument = insertDocument(cutoff.minusSeconds(1))
            val originalDeletePerformed = Instant.parse("2026-01-01T12:00:00Z")
            deleteContent(missingContentDocument.id)
            softDelete(softDeletedDocument.id, originalDeletePerformed)

            val firstResult = documentDAO.cleanupExpiredDocuments(cutoff, 500)
            val secondResult = documentDAO.cleanupExpiredDocuments(cutoff, 500)

            firstResult shouldBe DocumentCleanupBatchResult(processedCount = 2, deletedContentCount = 1)
            documentDAO.getById(missingContentDocument.id)?.deletePerformed shouldNotBe null
            documentDAO.getById(softDeletedDocument.id)?.deletePerformed shouldBe originalDeletePerformed
            documentContentDAO.getDocumentContentById(softDeletedDocument.id) shouldBe null
            secondResult shouldBe DocumentCleanupBatchResult(processedCount = 0, deletedContentCount = 0)
        }

        it("processes expired documents in bounded idempotent batches") {
            val cutoff = Instant.parse("2026-04-30T12:00:00Z")
            repeat(3) { offset ->
                insertDocument(cutoff.minusSeconds((offset + 1).toLong()))
            }

            documentDAO.cleanupExpiredDocuments(cutoff, 2) shouldBe
                DocumentCleanupBatchResult(processedCount = 2, deletedContentCount = 2)
            documentDAO.cleanupExpiredDocuments(cutoff, 2) shouldBe
                DocumentCleanupBatchResult(processedCount = 1, deletedContentCount = 1)
            documentDAO.cleanupExpiredDocuments(cutoff, 2) shouldBe
                DocumentCleanupBatchResult(processedCount = 0, deletedContentCount = 0)
        }

        it("marks an already cleaned document once and drains it from the working set") {
            val cutoff = Instant.parse("2026-04-30T12:00:00Z")
            val document = insertDocument(cutoff.minusSeconds(1))
            softDelete(document.id, Instant.parse("2026-01-01T12:00:00Z"))
            deleteContent(document.id)

            contentDeletedAt(document.id) shouldBe null
            documentDAO.cleanupExpiredDocuments(cutoff, 500) shouldBe
                DocumentCleanupBatchResult(processedCount = 1, deletedContentCount = 0)
            contentDeletedAt(document.id) shouldNotBe null
            documentDAO.cleanupExpiredDocuments(cutoff, 500) shouldBe
                DocumentCleanupBatchResult(processedCount = 0, deletedContentCount = 0)
        }
    })
