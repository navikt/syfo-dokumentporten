package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.document.db.Page.Meta
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val COUNT_COLUMN_NAME = "total_count"
private const val MAX_TRANSMISSION_OPENED_CLAIM_RELEASE_SIZE = 100

data class ClaimedTransmissionOpenedDocument(val document: PersistedDocumentEntity, val claimToken: UUID)

private fun selectDocWithDialogJoin(useCount: Boolean = false) =
    """
    SELECT${if (useCount) " COUNT(*) OVER() as $COUNT_COLUMN_NAME," else ""} doc.*, 
    dialog.id as dialog_pk_id, dialog.title as dialog_title, dialog.summary as dialog_summary, 
    dialog.dialogporten_uuid as dialog_uuid, dialog.fnr, dialog.org_number, dialog.created as dialog_created, 
    dialog.updated as dialog_updated
    FROM document doc
    LEFT JOIN dialog dialog ON doc.dialog_id = dialog.id
    """

class DocumentDAO(private val database: DatabaseInterface) {
    suspend fun insert(
        connection: Connection,
        documentEntity: DocumentEntity,
        content: ByteArray,
    ): PersistedDocumentEntity {
        val insertedDocument = connection.prepareStatement(
            """
                        INSERT INTO document(document_id,
                                             type,
                                             content_type,
                                             title,
                                             summary,
                                             link_id,
                                             status,
                                             dialog_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        RETURNING *;
            """.trimIndent()
        ).use { preparedStatement ->
            with(documentEntity) {
                var idx = 1
                preparedStatement.setObject(idx++, documentId)
                preparedStatement.setObject(idx++, type, Types.OTHER)
                preparedStatement.setString(idx++, contentType)
                preparedStatement.setString(idx++, title)
                preparedStatement.setString(idx++, summary)
                preparedStatement.setObject(idx++, linkId)
                preparedStatement.setObject(idx++, status, Types.OTHER)
                preparedStatement.setLong(idx++, dialog.id)
            }
            preparedStatement.execute()

            runCatching {
                if (preparedStatement.resultSet.next()) {
                    preparedStatement.resultSet.toDocumentEntity(documentEntity.dialog)
                } else {
                    throw DocumentInsertException("Could not get the inserted document.")
                }
            }.getOrElse {
                connection.rollback()
                throw it
            }
        }

        connection.prepareStatement(
            """
                        INSERT INTO document_content(id, content)
                        VALUES (?, ?)
            """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setLong(1, insertedDocument.id)
            preparedStatement.setBytes(2, content)
            preparedStatement.execute()
        }

        return insertedDocument
    }

    suspend fun update(documentEntity: PersistedDocumentEntity) {
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.prepareStatement(
                    """
                        UPDATE document
                        SET status     = ?,
                            is_read    = ?,
                            updated    = ?,
                            transmission_id = ?
                        WHERE id = ?
                    """.trimIndent()
                ).use { preparedStatement ->
                    with(documentEntity) {
                        preparedStatement.setObject(1, status, Types.OTHER)
                        preparedStatement.setBoolean(2, isRead)
                        preparedStatement.setTimestamp(3, Timestamp.from(updated))
                        preparedStatement.setObject(4, transmissionId)
                        preparedStatement.setLong(5, id)
                    }
                    preparedStatement.execute()
                }
                if (documentEntity.dialog.dialogportenUUID != null) {
                    connection.prepareStatement(
                        """
                        UPDATE dialog
                        SET dialogporten_uuid = ?,
                            updated   = ?
                        WHERE id = ?
                        """.trimIndent()
                    ).use { preparedStatement ->
                        with(documentEntity) {
                            preparedStatement.setObject(1, dialog.dialogportenUUID)
                            preparedStatement.setTimestamp(2, Timestamp.from(dialog.updated))
                            preparedStatement.setLong(3, dialog.id)
                        }
                        preparedStatement.execute()
                    }
                }
                connection.commit()
            }
        }
    }

    suspend fun getById(id: Long): PersistedDocumentEntity? = withContext(Dispatchers.IO) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                        ${selectDocWithDialogJoin()}
                        WHERE doc.id = ?
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setLong(1, id)
                val resultSet = preparedStatement.executeQuery()
                if (resultSet.next()) {
                    resultSet.toDocumentEntity()
                } else {
                    null
                }
            }
        }
    }

    suspend fun getByLinkId(linkId: UUID): PersistedDocumentEntity? = withContext(Dispatchers.IO) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                        ${selectDocWithDialogJoin()}
                        WHERE doc.link_id = ?
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setObject(1, linkId)
                val resultSet = preparedStatement.executeQuery()
                if (resultSet.next()) {
                    resultSet.toDocumentEntity()
                } else {
                    null
                }
            }
        }
    }

    suspend fun markGuiOpened(documentId: Long) {
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.prepareStatement(
                    """
                    UPDATE document
                    SET gui_opened_at = NOW()
                    WHERE id = ?
                        AND gui_opened_at IS NULL
                    """.trimIndent()
                ).use { preparedStatement ->
                    preparedStatement.setLong(1, documentId)
                    preparedStatement.executeUpdate()
                }
                connection.commit()
            }
        }
    }

    suspend fun claimDocumentsWithUnsentTransmissionOpenedActivities(
        limit: Int = 100,
        leaseDuration: Duration = Duration.ofMinutes(10),
    ): List<ClaimedTransmissionOpenedDocument> {
        require(limit > 0) { "limit must be positive" }
        require(!leaseDuration.isNegative && !leaseDuration.isZero && leaseDuration.toMillis() > 0) {
            "leaseDuration must be positive"
        }

        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                val documents = connection.prepareStatement(
                    """
                    ${selectDocWithDialogJoin()}
                    WHERE doc.gui_opened_at IS NOT NULL
                        AND doc.transmission_opened_sent_at IS NULL
                        AND doc.transmission_opened_failed_at IS NULL
                        AND doc.delete_performed IS NULL
                        AND dialog.dialogporten_uuid IS NOT NULL
                        AND doc.transmission_id IS NOT NULL
                        AND (
                            doc.transmission_opened_claim_until IS NULL
                            OR doc.transmission_opened_claim_until < CURRENT_TIMESTAMP
                        )
                    ORDER BY doc.gui_opened_at
                    LIMIT ?
                    FOR UPDATE OF doc SKIP LOCKED
                    """.trimIndent()
                ).use { preparedStatement ->
                    preparedStatement.setInt(1, limit)
                    preparedStatement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(resultSet.toDocumentEntity())
                            }
                        }
                    }
                }

                if (documents.isEmpty()) {
                    connection.commit()
                    return@use emptyList()
                }

                val claimToken = UUID.randomUUID()
                val idPlaceholders = documents.joinToString(", ") { "?" }
                val updatedRows = connection.prepareStatement(
                    """
                    UPDATE document
                    SET transmission_opened_claim_token = ?,
                        transmission_opened_claim_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')
                    WHERE id IN ($idPlaceholders)
                    """.trimIndent()
                ).use { preparedStatement ->
                    preparedStatement.setObject(1, claimToken)
                    preparedStatement.setLong(2, leaseDuration.toMillis())
                    documents.forEachIndexed { index, document ->
                        preparedStatement.setLong(index + 3, document.id)
                    }
                    preparedStatement.executeUpdate()
                }
                if (updatedRows != documents.size) {
                    connection.rollback()
                    error("Could not claim all selected TransmissionOpened documents")
                }
                connection.commit()

                documents.map { ClaimedTransmissionOpenedDocument(it, claimToken) }
            }
        }
    }

    suspend fun releaseTransmissionOpenedClaims(documentIds: List<Long>, claimToken: UUID): Int {
        if (documentIds.isEmpty()) {
            return 0
        }
        require(documentIds.size <= MAX_TRANSMISSION_OPENED_CLAIM_RELEASE_SIZE) {
            "documentIds cannot exceed $MAX_TRANSMISSION_OPENED_CLAIM_RELEASE_SIZE"
        }

        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                val idPlaceholders = documentIds.joinToString(", ") { "?" }
                val updatedRows = connection.prepareStatement(
                    """
                    UPDATE document
                    SET transmission_opened_claim_token = NULL,
                        transmission_opened_claim_until = NULL
                    WHERE id IN ($idPlaceholders)
                        AND transmission_opened_claim_token = ?
                        AND transmission_opened_sent_at IS NULL
                        AND transmission_opened_failed_at IS NULL
                        AND delete_performed IS NULL
                    """.trimIndent()
                ).use { preparedStatement ->
                    documentIds.forEachIndexed { index, documentId ->
                        preparedStatement.setLong(index + 1, documentId)
                    }
                    preparedStatement.setObject(documentIds.size + 1, claimToken)
                    preparedStatement.executeUpdate()
                }
                connection.commit()
                updatedRows
            }
        }
    }

    suspend fun setTransmissionOpenedInDialogporten(documentId: Long, claimToken: UUID): Boolean =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                connection.prepareStatement(
                    """
                    UPDATE document
                    SET transmission_opened_sent_at = NOW(),
                        transmission_opened_claim_token = NULL,
                        transmission_opened_claim_until = NULL
                    WHERE id = ?
                        AND transmission_opened_claim_token = ?
                        AND transmission_opened_sent_at IS NULL
                        AND transmission_opened_failed_at IS NULL
                        AND delete_performed IS NULL
                    """.trimIndent()
                ).use { preparedStatement ->
                    preparedStatement.setLong(1, documentId)
                    preparedStatement.setObject(2, claimToken)
                    preparedStatement.executeUpdate()
                }.let { updatedRows ->
                    connection.commit()
                    updatedRows == 1
                }
            }
        }

    suspend fun persistSettingTransmissionOpenedFailed(documentId: Long, claimToken: UUID): Boolean =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                connection.prepareStatement(
                    """
                    UPDATE document
                    SET transmission_opened_failed_at = NOW(),
                        transmission_opened_claim_token = NULL,
                        transmission_opened_claim_until = NULL
                    WHERE id = ?
                        AND transmission_opened_claim_token = ?
                        AND transmission_opened_sent_at IS NULL
                        AND transmission_opened_failed_at IS NULL
                        AND delete_performed IS NULL
                    """.trimIndent()
                ).use { preparedStatement ->
                    preparedStatement.setLong(1, documentId)
                    preparedStatement.setObject(2, claimToken)
                    preparedStatement.executeUpdate()
                }.let { updatedRows ->
                    connection.commit()
                    updatedRows == 1
                }
            }
        }

    suspend fun getDocumentsByStatus(status: DocumentStatus, limit: Int = 100): List<PersistedDocumentEntity> =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.prepareStatement(
                    """
                        ${selectDocWithDialogJoin()} 
                        WHERE doc.status = ?
                        AND doc.delete_performed IS NULL
                        order by doc.created 
                        LIMIT ? 
                    """.trimIndent()
                ).use { preparedStatement ->
                    preparedStatement.setObject(1, status, Types.OTHER)
                    preparedStatement.setInt(2, limit)
                    val resultSet = preparedStatement.executeQuery()
                    val documents = mutableListOf<PersistedDocumentEntity>()
                    while (resultSet.next()) {
                        documents.add(resultSet.toDocumentEntity())
                    }
                    documents
                }
            }
        }

    suspend fun findDocumentsByParameters(
        isRead: Boolean? = null,
        type: DocumentType? = null,
        orgnumber: String? = null,
        createdAfter: Instant? = null,
        createdBefore: Instant? = null,
        orderBy: SqlFilterBuilder.OrderBy = SqlFilterBuilder.OrderBy.CREATED,
        pageSize: Int,
        orderDirection: Page.OrderDirection = Page.OrderDirection.ASC,
    ): Page<PersistedDocumentEntity> {
        val limitInRange = pageSize.coerceIn(1, Page.MAX_PAGE_SIZE)

        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                val preparedStatement = SqlFilterBuilder().let { builder ->
                    builder
                        .filterParam("doc.is_read", isRead)
                        .filterParam("doc.type", type)
                        .filterParam("dialog.org_number", orgnumber)
                        .filterParam("doc.delete_performed", null, SqlFilterBuilder.ComparisonOperator.IS)
                        .filterParam(
                            "doc.created",
                            createdAfter,
                            SqlFilterBuilder.ComparisonOperator.GREATER_THAN
                        )
                        .filterParam(
                            "doc.created",
                            createdBefore,
                            SqlFilterBuilder.ComparisonOperator.LESS_THAN
                        )

                    builder.orderBy = orderBy
                    builder.limit = limitInRange
                    builder.orderDirection = orderDirection

                    builder.buildStatement(
                        connection.prepareStatement(
                            """
                            ${selectDocWithDialogJoin(true)}
                            ${builder.buildFilterString()}
                            """.trimIndent(),
                            ResultSet.TYPE_FORWARD_ONLY,
                            ResultSet.CONCUR_READ_ONLY
                        )
                    )
                }

                preparedStatement.use {
                    val resultSet = it.executeQuery()
                    var totalCount = 0L

                    val docs = buildList {
                        if (resultSet.next()) {
                            totalCount = resultSet.getLong(COUNT_COLUMN_NAME)
                            add(resultSet.toDocumentEntity())

                            while (resultSet.next()) {
                                add(resultSet.toDocumentEntity())
                            }
                        }
                    }

                    Page(
                        meta = Meta(
                            size = docs.size,
                            pageSize = limitInRange,
                            hasMore = totalCount > docs.size,
                            resultSize = totalCount,
                        ),
                        items = docs,
                    )
                }
            }
        }
    }
}

fun ResultSet.toDocumentEntity(withDialog: PersistedDialogEntity? = null): PersistedDocumentEntity =
    PersistedDocumentEntity(
        id = getLong("id"),
        linkId = getObject("link_id") as UUID,
        documentId = getObject("document_id") as UUID,
        type = DocumentType.valueOf(getString("type")),
        contentType = getString("content_type"),
        title = getString("title"),
        summary = getString("summary"),
        status = DocumentStatus.valueOf(getString("status")),
        isRead = getBoolean("is_read"),
        transmissionId = getObject("transmission_id") as UUID?,
        deletePerformed = getTimestamp("delete_performed")?.toInstant(),
        guiOpenedAt = getTimestamp("gui_opened_at")?.toInstant(),
        transmissionOpenedSentAt = getTimestamp("transmission_opened_sent_at")?.toInstant(),
        transmissionOpenedFailedAt = getTimestamp("transmission_opened_failed_at")?.toInstant(),
        created = getTimestamp("created").toInstant(),
        updated = getTimestamp("updated").toInstant(),
        dialog = withDialog ?: PersistedDialogEntity(
            id = getLong("dialog_pk_id"),
            title = getString("dialog_title"),
            summary = getString("dialog_summary"),
            fnr = getString("fnr"),
            orgNumber = getString("org_number"),
            dialogportenUUID = getObject("dialog_uuid") as UUID?,
            created = getTimestamp("dialog_created").toInstant(),
            updated = getTimestamp("dialog_updated").toInstant(),
        ),
    )

class DocumentInsertException(message: String) : RuntimeException(message)
