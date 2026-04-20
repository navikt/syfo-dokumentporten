package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.document.api.v1.dto.ArbeidsgiverVarselType
import java.sql.ResultSet
import java.sql.Types

class VarselInstruksDAO(private val database: DatabaseInterface) {

    suspend fun insert(documentId: Long, varselType: ArbeidsgiverVarselType): VarselInstruksEntity {
        val insertStatement =
            """
            INSERT INTO varsel_instruks (document_id, varsel_type)
            VALUES (?, ?)
            RETURNING *
            """.trimIndent()

        return withContext(Dispatchers.IO) {
            database.connection.use { conn ->
                conn.prepareStatement(insertStatement).use { ps ->
                    ps.setLong(1, documentId)
                    ps.setObject(2, varselType.name, Types.OTHER)
                    val resultSet = ps.executeQuery()
                    if (resultSet.next()) {
                        resultSet.toVarselInstruksEntity()
                    } else {
                        throw IllegalStateException("Inserting varsel_instruks failed, no rows returned.")
                    }
                }.also {
                    conn.commit()
                }
            }
        }
    }

    suspend fun getByDocumentId(documentId: Long): VarselInstruksEntity? {
        val query =
            """
            SELECT * FROM varsel_instruks WHERE document_id = ?
            """.trimIndent()

        return withContext(Dispatchers.IO) {
            database.connection.use { conn ->
                conn.prepareStatement(query).use { ps ->
                    ps.setLong(1, documentId)
                    val resultSet = ps.executeQuery()
                    if (resultSet.next()) {
                        resultSet.toVarselInstruksEntity()
                    } else {
                        null
                    }
                }
            }
        }
    }
}

fun ResultSet.toVarselInstruksEntity(): VarselInstruksEntity = VarselInstruksEntity(
    id = getLong("id"),
    documentId = getLong("document_id"),
    varselType = ArbeidsgiverVarselType.valueOf(getString("varsel_type")),
    created = getTimestamp("created").toInstant(),
)
