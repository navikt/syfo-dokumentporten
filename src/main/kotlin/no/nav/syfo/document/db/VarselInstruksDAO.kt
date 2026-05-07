package no.nav.syfo.document.db

import no.nav.syfo.document.api.v1.dto.VarselInstruks
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.case
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private val PENDING_GRACE_PERIOD: Long = 1
private val PROCESSING_RETRY_PERIOD: Long = 5

class VarselInstruksDAO(private val database: Database) {

    fun insert(
        documentId: Long,
        ressursId: String,
        ressursUrl: String,
        varselInstruks: VarselInstruks
    ): VarselInstruksEntity = VarselInstruksTable.insertReturning {
        it[VarselInstruksTable.documentId] = documentId
        it[VarselInstruksTable.type] = varselInstruks.type.name
        it[VarselInstruksTable.epostTittel] = varselInstruks.notifikasjonInnhold.epostTittel
        it[VarselInstruksTable.epostBody] = varselInstruks.notifikasjonInnhold.epostBody
        it[VarselInstruksTable.smsTekst] = varselInstruks.notifikasjonInnhold.smsTekst
        it[VarselInstruksTable.ressursId] = ressursId
        it[VarselInstruksTable.ressursUrl] = ressursUrl
        it[VarselInstruksTable.kilde] = varselInstruks.kilde
    }.singleOrNull()?.toVarselInstruksEntity()
        ?: throw IllegalStateException("Inserting varsel_instruks failed, no rows returned.")

    suspend fun getByDocumentId(documentId: Long): VarselInstruksEntity? = suspendTransaction(
        db = database,
    ) {
        VarselInstruksTable
            .selectAll()
            .where { VarselInstruksTable.documentId eq documentId }
            .singleOrNull()
            ?.toVarselInstruksEntity()
    }

    suspend fun getPendingForPublish(limit: Int): List<VarselInstruksPublishView> = suspendTransaction(
        db = database,
    ) {
        val pendingBefore = Instant.now().minus(PENDING_GRACE_PERIOD, ChronoUnit.MINUTES)
        val processingBefore = Instant.now().minus(PROCESSING_RETRY_PERIOD, ChronoUnit.MINUTES)

        VarselInstruksTable
            .join(
                otherTable = DocumentForVarselPublishTable,
                joinType = JoinType.INNER,
                additionalConstraint = {
                    VarselInstruksTable.documentId eq DocumentForVarselPublishTable.id
                },
            )
            .join(
                otherTable = DialogForVarselPublishTable,
                joinType = JoinType.INNER,
                additionalConstraint = {
                    DocumentForVarselPublishTable.dialogId eq DialogForVarselPublishTable.id
                },
            )
            .select(
                listOf(
                    VarselInstruksTable.id,
                    DocumentForVarselPublishTable.documentId,
                    DialogForVarselPublishTable.fnr,
                    DialogForVarselPublishTable.orgNumber,
                    VarselInstruksTable.ressursId,
                    VarselInstruksTable.ressursUrl,
                    VarselInstruksTable.kilde,
                    VarselInstruksTable.epostTittel,
                    VarselInstruksTable.epostBody,
                    VarselInstruksTable.smsTekst,
                )
            )
            .where {
                (
                    (VarselInstruksTable.status eq VarselInstruksStatus.PENDING.name) and
                        (VarselInstruksTable.created less pendingBefore.atOffset(ZoneOffset.UTC))
                    ) or
                    (
                        (VarselInstruksTable.status eq VarselInstruksStatus.PROCESSING.name) and
                            (VarselInstruksTable.created less processingBefore.atOffset(ZoneOffset.UTC))
                        )
            }
            .orderBy(VarselInstruksTable.created to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                VarselInstruksPublishView(
                    id = row[VarselInstruksTable.id],
                    documentId = row[DocumentForVarselPublishTable.documentId],
                    fnr = row[DialogForVarselPublishTable.fnr],
                    orgNumber = row[DialogForVarselPublishTable.orgNumber],
                    ressursId = row[VarselInstruksTable.ressursId],
                    dokumentUrl = row[VarselInstruksTable.ressursUrl],
                    kilde = row[VarselInstruksTable.kilde],
                    epostTittel = row[VarselInstruksTable.epostTittel],
                    epostBody = row[VarselInstruksTable.epostBody],
                    smsTekst = row[VarselInstruksTable.smsTekst],
                )
            }
    }

    fun markPublished(id: Long, publishedAt: Instant) {
        VarselInstruksTable.update({ VarselInstruksTable.id eq id }) {
            it[VarselInstruksTable.status] = VarselInstruksStatus.PUBLISHED.name
            it[VarselInstruksTable.publishedAt] = publishedAt.atOffset(ZoneOffset.UTC)
            it[VarselInstruksTable.publishAttempts] = VarselInstruksTable.publishAttempts + intLiteral(1)
            it[VarselInstruksTable.lastPublishError] = null
        }
    }

    fun markPublishError(id: Long, error: String, isPermanentError: Boolean) {
        val statusExpression = if (isPermanentError) {
            case()
                .When(
                    (VarselInstruksTable.publishAttempts + intLiteral(1)) greaterEq intLiteral(10),
                    stringLiteral(VarselInstruksStatus.ERROR.name),
                )
                .Else(stringLiteral(VarselInstruksStatus.PENDING.name))
        } else {
            stringLiteral(VarselInstruksStatus.PENDING.name)
        }

        VarselInstruksTable.update({ VarselInstruksTable.id eq id }) {
            it[VarselInstruksTable.status] = statusExpression
            it[VarselInstruksTable.publishAttempts] = VarselInstruksTable.publishAttempts + intLiteral(1)
            it[VarselInstruksTable.lastPublishError] = error
        }
    }
}

private object DocumentForVarselPublishTable : Table("document") {
    val id = long("id")
    val documentId = javaUUID("document_id")
    val dialogId = long("dialog_id")
}

private object DialogForVarselPublishTable : Table("dialog") {
    val id = long("id")
    val fnr = varchar("fnr", length = 11)
    val orgNumber = varchar("org_number", length = 9)
}
