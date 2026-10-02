package no.nav.syfo.document.db.exposed

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object DocumentTable : Table("document") {
    val id = long("id")
    val documentId = javaUUID("document_id")
    val dialogId = long("dialog_id")
    val updated = timestampWithTimeZone("updated")
    val deletePerformed = timestampWithTimeZone("delete_performed").nullable()

    override val primaryKey = PrimaryKey(id)
}
