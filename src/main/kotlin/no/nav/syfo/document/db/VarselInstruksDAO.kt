package no.nav.syfo.document.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.document.api.v1.dto.HendelseType
import no.nav.syfo.document.api.v1.dto.VarselInstruks
import java.sql.Connection
import java.sql.ResultSet

class VarselInstruksDAO(private val database: DatabaseInterface) {

    fun insert(
        connection: Connection,
        documentId: Long,
        ressursId: String,
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
            ps.setString(7, varselInstruks.ressursUrl)
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
)
