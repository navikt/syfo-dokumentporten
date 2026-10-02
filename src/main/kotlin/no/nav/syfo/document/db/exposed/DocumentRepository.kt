package no.nav.syfo.document.db.exposed

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class DocumentRepository(private val database: Database) {
    /**
     * Sets delete_performed on all documents with the given document_id that are not already deleted.
     * Returns false if no document with the given document_id exists.
     */
    suspend fun softDeleteByDocumentId(documentId: UUID): Boolean = suspendTransaction(db = database) {
        val updatedRows = DocumentTable.update({
            (DocumentTable.documentId eq documentId) and DocumentTable.deletePerformed.isNull()
        }) {
            it[DocumentTable.deletePerformed] = CurrentTimestampWithTimeZone
            it[DocumentTable.updated] = CurrentTimestampWithTimeZone
        }
        updatedRows > 0 ||
            DocumentTable
                .select(DocumentTable.id)
                .where { DocumentTable.documentId eq documentId }
                .limit(1)
                .any()
    }
}
