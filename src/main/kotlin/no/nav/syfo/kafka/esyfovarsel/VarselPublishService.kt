package no.nav.syfo.kafka.esyfovarsel

import no.nav.syfo.document.db.PersistedDocumentEntity
import no.nav.syfo.document.db.VarselInstruksDAO
import no.nav.syfo.document.db.VarselInstruksPublishView
import no.nav.syfo.document.db.toPublishView
import no.nav.syfo.util.logger
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.TimeoutException
import java.net.ConnectException
import java.sql.Connection
import java.time.Instant

private const val PUBLISH_VARSEL_BATCH_LIMIT = 100

class VarselPublishService(
    private val varselInstruksDAO: VarselInstruksDAO,
    private val esyfovarselProducer: IEsyfovarselProducer,
) {
    private val logger = logger()

    suspend fun publishPendingVarsler() {
        var batchSize: Int

        do {
            val pendingVarsler = varselInstruksDAO.getPendingForPublish(PUBLISH_VARSEL_BATCH_LIMIT)
            batchSize = pendingVarsler.size

            pendingVarsler.forEach { view ->
                publishPendingVarsel(view)
            }
        } while (batchSize >= PUBLISH_VARSEL_BATCH_LIMIT)
    }

    suspend fun publishVarselForDocument(document: PersistedDocumentEntity) {
        varselInstruksDAO.getByDocumentId(document.id)?.let { varselinstruks ->
            varselInstruksDAO.withConnection { connection ->
                try {
                    val varselinstruksView = varselinstruks.toPublishView(document)
                    publishVarsel(varselinstruksView, connection)
                    connection.commit()
                    logger.info("Varsel publisert for document med intern id ${document.id}")
                } catch (exception: Exception) {
                    connection.rollback()
                    throw exception
                }
            }
        } ?: logger.warn(
            "Fant ikke varsel for dokument med intern id ${document.id}"
        )
    }

    private fun publishVarsel(view: VarselInstruksPublishView, connection: Connection) {
        try {
            esyfovarselProducer.publish(
                key = view.documentId.toString(),
                hendelse = view.toEsyfovarselHendelse(),
            )
            varselInstruksDAO.markPublished(connection, view.id, Instant.now())
            COUNT_VARSEL_PUBLISHED.increment()
        } catch (exception: Exception) {
            val rootCause = exception.rootCause()
            val isInfraError = rootCause is TimeoutException ||
                rootCause is AuthorizationException ||
                rootCause is ConnectException ||
                rootCause is InterruptedException

            varselInstruksDAO.markPublishError(
                connection = connection,
                id = view.id,
                error = rootCause.message ?: "unknown",
                isInfraError = isInfraError,
            )
            COUNT_VARSEL_PUBLISH_FAILED.increment()
            logger.error(
                "Failed to publish varsel_instruks ${view.id} to esyfovarsel. infraError=$isInfraError",
                exception,
            )
        }
    }

    private suspend fun publishPendingVarsel(view: VarselInstruksPublishView) {
        varselInstruksDAO.withConnection { connection ->
            try {
                publishVarsel(view, connection)
                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            }
        }
    }
}

private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()
