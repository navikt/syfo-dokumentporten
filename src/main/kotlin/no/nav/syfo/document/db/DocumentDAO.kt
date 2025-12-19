package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.document.api.v1.dto.DocumentType
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.*
import kotlin.math.ceil

private const val COUNT_COLUMN_NAME = "total_count"

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
    suspend fun insert(documentEntity: DocumentEntity, content: ByteArray): PersistedDocumentEntity {
        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
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
                        } else throw DocumentInsertException("Could not get the inserted document.")
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

                connection.commit()
                insertedDocument
            }
        }
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

    suspend fun getById(id: Long): PersistedDocumentEntity? {
        return withContext(Dispatchers.IO) {
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
    }

    suspend fun getByLinkId(linkId: UUID): PersistedDocumentEntity? {
        return withContext(Dispatchers.IO) {
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
    }

    suspend fun getDocumentsByStatus(status: DocumentStatus): List<PersistedDocumentEntity> {
        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.prepareStatement(
                    """
                        ${selectDocWithDialogJoin()} 
                        WHERE doc.status = ? 
                        order by doc.created 
                        LIMIT 100 
                        """.trimIndent()
                ).use { preparedStatement ->
                    preparedStatement.setObject(1, status, Types.OTHER)
                    val resultSet = preparedStatement.executeQuery()
                    val documents = mutableListOf<PersistedDocumentEntity>()
                    while (resultSet.next()) {
                        documents.add(resultSet.toDocumentEntity())
                    }
                    documents
                }
            }
        }
    }

    suspend fun findDocumentsByParameters(
        pageSize: Int,
        page: Int,
        orgnumber: String? = null,
        type: DocumentType? = null,
        contentType: String? = null,
        status: DocumentStatus? = null,
        isRead: Boolean = false,
        transmissionId: String? = null,
        createdAfter: Instant? = null,
        updatedAfter: Instant? = null,
        createdBefore: Instant? = null,
        updatedBefore: Instant? = null,
        dialogId: String? = null,
        orderBy: Page.OrderBy = Page.OrderBy.CREATED,
        orderDirection: Page.OrderDirection = Page.OrderDirection.DESC,
    ): Page<PersistedDocumentEntity> {
        val limitInRange = pageSize.coerceIn(1, Page.MAX_PAGE_SIZE)

        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                val preparedStatement = SqlFilterBuilder().run {
                    filterParam("doc.is_read", isRead)
                    filterParam("doc.type", type)
                    filterParam("doc.content_type", contentType)
                    filterParam("doc.status", status)
                    filterParam("doc.transmission_id", transmissionId)
                    filterParam("doc.dialog_id", dialogId)
                    filterParam("dialog.org_number", orgnumber)
                    filterParam(
                        "doc.created",
                        createdAfter,
                        SqlFilterBuilder.ComparisonOperator.GREATER_THAN
                    )
                    filterParam(
                        "doc.updated",
                        updatedAfter,
                        SqlFilterBuilder.ComparisonOperator.GREATER_THAN
                    )
                    filterParam(
                        "doc.created",
                        createdBefore,
                        SqlFilterBuilder.ComparisonOperator.LESS_THAN
                    )
                    filterParam(
                        "doc.updated",
                        updatedBefore,
                        SqlFilterBuilder.ComparisonOperator.LESS_THAN
                    )

                    this.orderBy = orderBy
                    this.limit = pageSize
                    this.orderDirection = orderDirection
                    this.offset = page * limitInRange

                    buildStatement(
                        connection.prepareStatement(
                            """
                            ${selectDocWithDialogJoin(true)}
                            ${buildFilterString()}
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
                            // Probably faster to "manually" get the first item to fetch the count, than to use a scrollable ResultSet
                            totalCount = resultSet.getLong(COUNT_COLUMN_NAME)
                            add(resultSet.toDocumentEntity())

                            while (resultSet.next()) {
                                add(resultSet.toDocumentEntity())
                            }
                        }
                    }

                    Page(
                        page = page,
                        totalPages = ceil(totalCount.toDouble() / limitInRange).toInt(),
                        totalElements = totalCount,
                        pageSize = limitInRange,
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
