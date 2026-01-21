package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface

class DocumentContentDAO(private val database: DatabaseInterface) {
    suspend fun getDocumentContentById(id: Long): ByteArray? = withContext(Dispatchers.IO) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT content
                FROM document_content
                WHERE id = ?
                """.trimIndent(),
            ).use { preparedStatement ->
                preparedStatement.setLong(1, id)
                preparedStatement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        resultSet.getBytes("content")
                    } else {
                        null
                    }
                }
            }
        }
    }
}
