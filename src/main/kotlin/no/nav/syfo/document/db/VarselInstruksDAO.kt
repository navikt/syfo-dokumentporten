package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.document.api.v1.dto.HendelseType
import no.nav.syfo.document.api.v1.dto.VarselInstruks
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class VarselInstruksDAO(private val database: DatabaseInterface) {

    fun insert(
        connection: Connection,
        documentId: Long,
        ressursId: String,
        ressursUrl: String,
        varselInstruks: VarselInstruks
    ): VarselInstruksEntity {
        val insertStatement =
            """
            INSERT INTO varsel_instruks (document_id, type, epost_tittel, epost_body, sms_tekst, ressurs_id, ressurs_url, kilde)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
            """.trimIndent()

        return connection.prepareStatement(insertStatement).use { ps ->
            ps.setLong(1, documentId)
            ps.setString(2, varselInstruks.type.name)
            ps.setString(3, varselInstruks.notifikasjonInnhold.epostTittel)
            ps.setString(4, varselInstruks.notifikasjonInnhold.epostBody)
            ps.setString(5, varselInstruks.notifikasjonInnhold.smsTekst)
            ps.setString(6, ressursId)
            ps.setString(7, ressursUrl)
            ps.setString(8, varselInstruks.kilde)
            val resultSet = ps.executeQuery()
            if (resultSet.next()) {
                resultSet.toVarselInstruksEntity()
            } else {
                throw IllegalStateException("Inserting varsel_instruks failed, no rows returned.")
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

    suspend fun getPendingForPublish(limit: Int): List<VarselInstruksPublishView> = withContext(Dispatchers.IO) {
        database.connection.use { conn ->
            getPendingForPublish(conn, limit)
        }
    }

    fun getPendingForPublish(connection: Connection, limit: Int): List<VarselInstruksPublishView> {
        val query =
            """
            SELECT
                vi.id,
                doc.document_id,
                dialog.fnr,
                dialog.org_number,
                vi.ressurs_id,
                vi.ressurs_url,
                vi.kilde,
                vi.epost_tittel,
                vi.epost_body,
                vi.sms_tekst
            FROM varsel_instruks vi
            INNER JOIN document doc ON vi.document_id = doc.id
            INNER JOIN dialog ON doc.dialog_id = dialog.id
            WHERE vi.status = 'PENDING'
            ORDER BY vi.created
            LIMIT ?
            FOR UPDATE OF vi SKIP LOCKED
            """.trimIndent()

        return connection.prepareStatement(query).use { ps ->
            ps.setInt(1, limit)
            val resultSet = ps.executeQuery()
            buildList {
                while (resultSet.next()) {
                    add(resultSet.toVarselInstruksPublishView())
                }
            }
        }
    }

    suspend fun <T> withConnection(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        database.connection.use(block)
    }

    fun markPublished(connection: Connection, id: Long, publishedAt: java.time.Instant) {
        val updateStatement =
            """
            UPDATE varsel_instruks
            SET status = 'PUBLISHED',
                published_at = ?,
                publish_attempts = publish_attempts + 1
            WHERE id = ?
            """.trimIndent()

        connection.prepareStatement(updateStatement).use { ps ->
            ps.setTimestamp(1, Timestamp.from(publishedAt))
            ps.setLong(2, id)
            ps.executeUpdate()
        }
    }

    fun markPublishError(connection: Connection, id: Long, error: String, isInfraError: Boolean) {
        val updateStatement =
            """
            UPDATE varsel_instruks
            SET status = CASE
                WHEN NOT ? AND publish_attempts + 1 >= 10 THEN 'ERROR'
                ELSE 'PENDING'
            END,
                publish_attempts = publish_attempts + 1,
                last_publish_error = ?
            WHERE id = ?
            """.trimIndent()

        connection.prepareStatement(updateStatement).use { ps ->
            ps.setBoolean(1, isInfraError)
            ps.setString(2, error)
            ps.setLong(3, id)
            ps.executeUpdate()
        }
    }
}

fun ResultSet.toVarselInstruksEntity(): VarselInstruksEntity = VarselInstruksEntity(
    id = getLong("id"),
    documentId = getLong("document_id"),
    type = HendelseType.valueOf(getString("type")),
    epostTittel = getString("epost_tittel"),
    epostBody = getString("epost_body"),
    smsTekst = getString("sms_tekst"),
    ressursId = getString("ressurs_id"),
    ressursUrl = getString("ressurs_url"),
    kilde = getString("kilde"),
    created = getTimestamp("created").toInstant(),
    status = VarselInstruksStatus.valueOf(getString("status")),
    publishedAt = getTimestamp("published_at")?.toInstant(),
    publishAttempts = getInt("publish_attempts"),
    lastPublishError = getString("last_publish_error"),
)

fun ResultSet.toVarselInstruksPublishView(): VarselInstruksPublishView = VarselInstruksPublishView(
    id = getLong("id"),
    documentId = getObject("document_id", UUID::class.java),
    fnr = getString("fnr"),
    orgNumber = getString("org_number"),
    ressursId = getString("ressurs_id"),
    ressursUrl = getString("ressurs_url"),
    kilde = getString("kilde"),
    epostTittel = getString("epost_tittel"),
    epostBody = getString("epost_body"),
    smsTekst = getString("sms_tekst"),
)
