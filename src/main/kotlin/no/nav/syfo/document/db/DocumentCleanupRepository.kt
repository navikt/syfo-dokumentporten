package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import java.sql.Timestamp
import java.time.Instant

internal const val DOCUMENT_CLEANUP_ADVISORY_LOCK_NAMESPACE = 2_140_101_321
internal const val DOCUMENT_CLEANUP_ADVISORY_LOCK_KEY = 1

class DocumentCleanupRepository(private val database: DatabaseInterface) {
    suspend fun cleanupExpiredDocuments(cutoff: Instant, batchSize: Int): DocumentCleanupBatchResult {
        require(batchSize > 0) { "Batch size must be greater than zero" }

        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                try {
                    if (!connection.tryAcquireCleanupLock()) {
                        connection.commit()
                        return@use DocumentCleanupBatchResult.LockContended
                    }

                    val expiredIds = connection.selectExpiredDocumentIds(cutoff, batchSize)
                    if (expiredIds.isEmpty()) {
                        connection.commit()
                        return@use DocumentCleanupBatchResult.Completed(
                            processedCount = 0,
                            deletedContentCount = 0,
                        )
                    }

                    val idArray = connection.createArrayOf("bigint", expiredIds.toTypedArray())
                    try {
                        val deletedContentCount = connection.prepareStatement(
                            """
                            DELETE FROM document_content
                            WHERE id = ANY(?)
                            """.trimIndent()
                        ).use { preparedStatement ->
                            preparedStatement.setArray(1, idArray)
                            preparedStatement.executeUpdate()
                        }
                        val processedCount = connection.prepareStatement(
                            """
                            UPDATE document
                            SET delete_performed = COALESCE(delete_performed, CURRENT_TIMESTAMP),
                                content_deleted_at = CURRENT_TIMESTAMP
                            WHERE id = ANY(?)
                            """.trimIndent()
                        ).use { preparedStatement ->
                            preparedStatement.setArray(1, idArray)
                            preparedStatement.executeUpdate()
                        }

                        connection.commit()
                        DocumentCleanupBatchResult.Completed(processedCount, deletedContentCount)
                    } finally {
                        idArray.free()
                    }
                } catch (ex: Exception) {
                    runCatching { connection.rollback() }.onFailure(ex::addSuppressed)
                    throw ex
                }
            }
        }
    }

    private fun java.sql.Connection.tryAcquireCleanupLock(): Boolean = prepareStatement(
        "SELECT pg_try_advisory_xact_lock(?, ?)"
    ).use { preparedStatement ->
        preparedStatement.setInt(1, DOCUMENT_CLEANUP_ADVISORY_LOCK_NAMESPACE)
        preparedStatement.setInt(2, DOCUMENT_CLEANUP_ADVISORY_LOCK_KEY)
        preparedStatement.executeQuery().use { resultSet ->
            check(resultSet.next())
            resultSet.getBoolean(1)
        }
    }

    private fun java.sql.Connection.selectExpiredDocumentIds(cutoff: Instant, batchSize: Int): List<Long> =
        prepareStatement(
            """
            SELECT id
            FROM document
            WHERE content_deleted_at IS NULL
              AND created < ?
            ORDER BY created, id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setTimestamp(1, Timestamp.from(cutoff))
            preparedStatement.setInt(2, batchSize)
            preparedStatement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.getLong("id"))
                    }
                }
            }
        }
}

sealed interface DocumentCleanupBatchResult {
    data class Completed(val processedCount: Int, val deletedContentCount: Int) : DocumentCleanupBatchResult

    data object LockContended : DocumentCleanupBatchResult
}
