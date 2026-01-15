package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.util.UUID
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.document.api.v1.dto.DocumentType
import java.sql.Timestamp
import java.sql.Types

private const val SELECT_DOC_WITH_DIALOG_JOIN =
    """
    SELECT doc.*, 
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
                        $SELECT_DOC_WITH_DIALOG_JOIN
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
                        $SELECT_DOC_WITH_DIALOG_JOIN
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

    suspend fun getDocumentsByStatus(status: DocumentStatus, limit: Int): List<PersistedDocumentEntity> {
        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.prepareStatement(
                    """
                        $SELECT_DOC_WITH_DIALOG_JOIN
                        WHERE doc.status = ?
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
