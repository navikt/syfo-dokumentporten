package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

class DialogDAO(private val database: DatabaseInterface) {
    suspend fun insertDialog(dialogEntity: DialogEntity): PersistedDialogEntity {
        val insertStatement =
            """
            INSERT INTO dialog (
                title,
                summary,
                fnr,
                org_number,
                dialogporten_uuid,
                birth_date
            ) VALUES (?, ?, ?, ?, ?, ?)
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
                    ps.setObject(6, dialogEntity.birthDate)
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

    suspend fun updateDialogWithBirthDate(dialogId: Long, birthDate: LocalDate, title: String): PersistedDialogEntity {
        val updateStatement =
            """
            UPDATE dialog
            SET birth_date = ?,
                title      = ?,
                updated    = ?
            WHERE id = ?
            RETURNING *
            """.trimIndent()
        return withContext(Dispatchers.IO) {
            val connection = database.connection
            connection.use { conn ->
                conn.prepareStatement(updateStatement).use { ps ->
                    ps.setObject(1, birthDate)
                    ps.setString(2, title)
                    ps.setTimestamp(3, Timestamp.from(Instant.now()))
                    ps.setLong(4, dialogId)
                    val resultSet = ps.executeQuery()
                    return@use if (resultSet.next()) {
                        resultSet.toDialog()
                    } else {
                        throw Exception("Updating dialog birth date failed, no rows returned.")
                    }
                }.also {
                    conn.commit()
                }
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
    dialogportenUUID = getObject("dialogporten_uuid", java.util.UUID::class.java),
    birthDate = getObject("birth_date", LocalDate::class.java),
)
