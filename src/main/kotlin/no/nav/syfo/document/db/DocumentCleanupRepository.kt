package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Coalesce
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset

internal const val DOCUMENT_CLEANUP_ADVISORY_LOCK_NAMESPACE = 2_140_101_321
internal const val DOCUMENT_CLEANUP_ADVISORY_LOCK_KEY = 1

class DocumentCleanupRepository(private val database: Database) {
    suspend fun cleanupExpiredDocuments(cutoff: Instant, batchSize: Int): DocumentCleanupBatchResult {
        require(batchSize > 0) { "Batch size must be greater than zero" }

        return withContext(Dispatchers.IO) {
            suspendTransaction(db = database) {
                // Preserve one attempt per batch; the hourly loop handles failures on a later run.
                maxAttempts = 1
                if (!tryAcquireCleanupLock()) {
                    return@suspendTransaction DocumentCleanupBatchResult.LockContended
                }

                val expiredIds = DocumentForCleanupTable
                    .select(DocumentForCleanupTable.id)
                    .where {
                        DocumentForCleanupTable.contentDeletedAt.isNull() and
                            (DocumentForCleanupTable.created less cutoff.atOffset(ZoneOffset.UTC))
                    }
                    .orderBy(
                        DocumentForCleanupTable.created to SortOrder.ASC,
                        DocumentForCleanupTable.id to SortOrder.ASC,
                    )
                    .limit(batchSize)
                    .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
                    .map { it[DocumentForCleanupTable.id] }
                if (expiredIds.isEmpty()) {
                    return@suspendTransaction DocumentCleanupBatchResult.Completed(0, 0)
                }

                val deletedContentCount = DocumentContentForCleanupTable.deleteWhere {
                    id inList expiredIds
                }
                val processedCount = DocumentForCleanupTable.update({ DocumentForCleanupTable.id inList expiredIds }) {
                    it[deletePerformed] = Coalesce(deletePerformed, CurrentTimestampWithTimeZone)
                    it[contentDeletedAt] = CurrentTimestampWithTimeZone
                }
                DocumentCleanupBatchResult.Completed(processedCount, deletedContentCount)
            }
        }
    }

    private fun JdbcTransaction.tryAcquireCleanupLock(): Boolean = checkNotNull(
        exec(
            stmt = "SELECT pg_try_advisory_xact_lock(?, ?)",
            args = listOf(
                IntegerColumnType() to DOCUMENT_CLEANUP_ADVISORY_LOCK_NAMESPACE,
                IntegerColumnType() to DOCUMENT_CLEANUP_ADVISORY_LOCK_KEY,
            ),
            explicitStatementType = StatementType.SELECT,
        ) { resultSet ->
            check(resultSet.next())
            resultSet.getBoolean(1)
        }
    )
}

private object DocumentForCleanupTable : Table("document") {
    val id = long("id")
    val created = timestampWithTimeZone("created")
    val deletePerformed = timestampWithTimeZone("delete_performed").nullable()
    val contentDeletedAt = timestampWithTimeZone("content_deleted_at").nullable()
}

private object DocumentContentForCleanupTable : Table("document_content") {
    val id = long("id")
}

sealed interface DocumentCleanupBatchResult {
    data class Completed(val processedCount: Int, val deletedContentCount: Int) : DocumentCleanupBatchResult

    data object LockContended : DocumentCleanupBatchResult
}
