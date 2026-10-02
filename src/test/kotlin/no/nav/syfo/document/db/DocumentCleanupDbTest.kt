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
        val documentCleanupRepository = DocumentCleanupRepository(database)
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

            val result = documentCleanupRepository.cleanupExpiredDocuments(cutoff, 500)

            result shouldBe DocumentCleanupBatchResult.Completed(processedCount = 1, deletedContentCount = 1)
            documentDAO.getById(oldDocument.id)?.deletePerformed shouldNotBe null
            documentContentDAO.getDocumentContentById(oldDocument.id) shouldBe null
            documentDAO.getById(boundaryDocument.id)?.deletePerformed shouldBe null
            documentContentDAO.getDocumentContentById(boundaryDocument.id) shouldNotBe null
            documentDAO.getById(recentDocument.id)?.deletePerformed shouldBe null
            documentContentDAO.getDocumentContentById(recentDocument.id) shouldNotBe null
        }

        it("marks documents with missing and existing content as cleaned and drains them from the working set") {
            val cutoff = Instant.parse("2026-04-30T12:00:00Z")
            val missingContentDocument = insertDocument(cutoff.minusSeconds(2))
            val softDeletedDocument = insertDocument(cutoff.minusSeconds(1))
            val originalDeletePerformed = Instant.parse("2026-01-01T12:00:00Z")
            deleteContent(missingContentDocument.id)
            softDelete(softDeletedDocument.id, originalDeletePerformed)

            contentDeletedAt(missingContentDocument.id) shouldBe null
            contentDeletedAt(softDeletedDocument.id) shouldBe null

            val firstResult = documentCleanupRepository.cleanupExpiredDocuments(cutoff, 500)

            firstResult shouldBe DocumentCleanupBatchResult.Completed(processedCount = 2, deletedContentCount = 1)
            documentDAO.getById(missingContentDocument.id)?.deletePerformed shouldNotBe null
            documentDAO.getById(softDeletedDocument.id)?.deletePerformed shouldBe originalDeletePerformed
            documentContentDAO.getDocumentContentById(softDeletedDocument.id) shouldBe null
            contentDeletedAt(missingContentDocument.id) shouldNotBe null
            contentDeletedAt(softDeletedDocument.id) shouldNotBe null

            val secondResult = documentCleanupRepository.cleanupExpiredDocuments(cutoff, 500)

            secondResult shouldBe DocumentCleanupBatchResult.Completed(processedCount = 0, deletedContentCount = 0)
        }

        it("processes expired documents in bounded idempotent batches") {
            val cutoff = Instant.parse("2026-04-30T12:00:00Z")
            repeat(3) { offset ->
                insertDocument(cutoff.minusSeconds((offset + 1).toLong()))
            }

            documentCleanupRepository.cleanupExpiredDocuments(cutoff, 2) shouldBe
                DocumentCleanupBatchResult.Completed(processedCount = 2, deletedContentCount = 2)
            documentCleanupRepository.cleanupExpiredDocuments(cutoff, 2) shouldBe
                DocumentCleanupBatchResult.Completed(processedCount = 1, deletedContentCount = 1)
            documentCleanupRepository.cleanupExpiredDocuments(cutoff, 2) shouldBe
                DocumentCleanupBatchResult.Completed(processedCount = 0, deletedContentCount = 0)
        }

        it("does not mutate documents when another transaction holds the cleanup lock") {
            val cutoff = Instant.parse("2026-04-30T12:00:00Z")
            val document = insertDocument(cutoff.minusSeconds(1))

            database.connection.use { lockConnection ->
                lockConnection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?, ?)"
                ).use { statement ->
                    statement.setInt(1, DOCUMENT_CLEANUP_ADVISORY_LOCK_NAMESPACE)
                    statement.setInt(2, DOCUMENT_CLEANUP_ADVISORY_LOCK_KEY)
                    statement.executeQuery().use { resultSet ->
                        check(resultSet.next())
                    }
                }

                documentCleanupRepository.cleanupExpiredDocuments(cutoff, 500) shouldBe
                    DocumentCleanupBatchResult.LockContended

                documentDAO.getById(document.id)?.deletePerformed shouldBe null
                documentContentDAO.getDocumentContentById(document.id) shouldNotBe null
                contentDeletedAt(document.id) shouldBe null
                lockConnection.rollback()
            }
        }

        it("rolls back content deletion when updating the document fails") {
            val cutoff = Instant.parse("2026-04-30T12:00:00Z")
            val document = insertDocument(cutoff.minusSeconds(1))

            try {
                database.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(
                            """
                            CREATE FUNCTION fail_document_cleanup_update() RETURNS trigger
                            LANGUAGE plpgsql AS $$
                            BEGIN
                                RAISE EXCEPTION 'Expected cleanup test failure';
                            END;
                            $$;
                            """.trimIndent()
                        )
                        statement.execute(
                            """
                            CREATE TRIGGER fail_document_cleanup_update
                            BEFORE UPDATE ON document
                            FOR EACH ROW EXECUTE FUNCTION fail_document_cleanup_update();
                            """.trimIndent()
                        )
                    }
                    connection.commit()
                }

                val failure = runCatching {
                    documentCleanupRepository.cleanupExpiredDocuments(cutoff, 500)
                }.exceptionOrNull()

                failure shouldNotBe null
                documentDAO.getById(document.id)?.deletePerformed shouldBe null
                documentContentDAO.getDocumentContentById(document.id) shouldNotBe null
                contentDeletedAt(document.id) shouldBe null
            } finally {
                database.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("DROP TRIGGER IF EXISTS fail_document_cleanup_update ON document")
                        statement.execute("DROP FUNCTION IF EXISTS fail_document_cleanup_update()")
                    }
                    connection.commit()
                }
            }
        }
    })
