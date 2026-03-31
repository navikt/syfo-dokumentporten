package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant

class DialogDAO(private val database: DatabaseInterface) {
    suspend fun insertDialog(dialogEntity: DialogEntity): PersistedDialogEntity {
        val insertStatement =
            """
            INSERT INTO dialog (
                title,
                summary,
                fnr,
                org_number,
                dialogporten_uuid
            ) VALUES (?, ?, ?, ?, ?)
            RETURNING *
            """.trimIndent()
        return withContext(Dispatchers.IO) {
            val connection = database.connection
            connection.use { conn ->
                conn.prepareStatement(insertStatement).use { ps ->
                    ps.setString(1, dialogEntity.title)
                    ps.setString(2, dialogEntity.summary)
                    ps.setString(3, dialogEntity.fnr)
                    ps.setString(4, dialogEntity.orgNumber)
                    ps.setObject(5, dialogEntity.dialogportenUUID)
                    val resultSet = ps.executeQuery()
                    return@use if (resultSet.next()) {
                        resultSet.toDialog()
                    } else {
                        throw Exception("Inserting dialog failed, no rows returned.")
                    }
                }.also {
                    conn.commit()
                }
            }
        }
    }

    suspend fun getDialogAwaitingDeletionInDialogporten(limit: Int): List<PersistedDialogEntity> {
        val selectStatement =
            """
                SELECT dialog.*
                FROM dialog
                WHERE delete_performed is null
                AND dialogporten_uuid IS NOT NULL
                LIMIT ?
            """.trimIndent()
        return withContext(Dispatchers.IO) {
            database.connection.use { conn ->
                conn.prepareStatement(selectStatement).use { ps ->
                    ps.setInt(1, limit)
                    val resultSet = ps.executeQuery()
                    val dialogs = mutableListOf<PersistedDialogEntity>()
                    while (resultSet.next()) {
                        dialogs.add(resultSet.toDialog())
                    }
                    dialogs
                }
            }
        }
    }

    suspend fun updateDialogportenAfterDelete(entity: PersistedDialogEntity) {
        withContext(Dispatchers.IO) {
            database.connection.use { conn ->
                if (entity.dialogportenUUID == null) {
                    conn.prepareStatement(
                        """
                        UPDATE document
                        SET transmission_id = ?,
                            status          = ?
                        WHERE dialog_id = ?
                        """.trimIndent()
                    ).use { ps ->
                        ps.setObject(1, null)
                        ps.setObject(2, DocumentStatus.RECEIVED, Types.OTHER)
                        ps.setLong(3, entity.id)
                        ps.executeUpdate()
                    }
                }
                conn.prepareStatement(
                    """
                        UPDATE dialog
                        SET dialogporten_uuid = ?,
                            updated           = ?,
                            delete_performed  = ?
                        WHERE id = ?
                    """.trimIndent()
                ).use { ps ->
                    with(entity) {
                        ps.setObject(1, dialogportenUUID)
                        ps.setTimestamp(2, Timestamp.from(updated))
                        ps.setTimestamp(3, Timestamp.from(Instant.now()))
                        ps.setLong(4, id)
                    }
                    ps.executeUpdate()
                }
                conn.commit()
            }
        }
    }

    suspend fun getByFnrAndOrgNumber(fnr: String, orgNumber: String): PersistedDialogEntity? {
        val query =
            """
            SELECT *
            FROM dialog
            WHERE fnr = ?
            AND org_number = ?
            """.trimIndent()

        return withContext(Dispatchers.IO) {
            database.connection.use { conn ->
                val preparedStatement = conn.prepareStatement(query)
                preparedStatement.use { ps ->
                    ps.setString(1, fnr)
                    ps.setString(2, orgNumber)
                    val resultSet = ps.executeQuery()
                    if (resultSet.next()) {
                        return@withContext resultSet.toDialog()
                    }
                    return@withContext null
                }
            }
        }
    }
}

fun ResultSet.toDialog(): PersistedDialogEntity = PersistedDialogEntity(
    id = getLong("id"),
    title = getString("title"),
    summary = getString("summary"),
    fnr = getString("fnr"),
    orgNumber = getString("org_number"),
    created = getTimestamp("created").toInstant(),
    updated = getTimestamp("updated").toInstant(),
    dialogportenUUID = getObject("dialogporten_uuid", java.util.UUID::class.java)
)
