package no.nav.syfo.document.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object VarselInstruksTable : Table("varsel_instruks") {
    val id = long("id").autoIncrement()
    val documentId = long("document_id").uniqueIndex()
    val type = text("type")
    val epostTittel = text("epost_tittel")
    val epostBody = text("epost_body")
    val smsTekst = text("sms_tekst")
    val ressursId = text("ressurs_id")
    val ressursUrl = text("ressurs_url")
    val kilde = text("kilde")
    val created = timestampWithTimeZone("created").defaultExpression(CurrentTimestampWithTimeZone)
    val status = text("status").default(VarselInstruksStatus.PENDING.name)
    val publishedAt = timestampWithTimeZone("published_at").nullable()
    val publishAttempts = integer("publish_attempts").default(0)
    val lastPublishError = text("last_publish_error").nullable()

    override val primaryKey = PrimaryKey(id)
}
