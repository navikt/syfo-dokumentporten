package no.nav.syfo.kafka.esyfovarsel

import no.nav.syfo.document.db.VarselInstruksDAO
import no.nav.syfo.util.logger
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.TimeoutException
import java.net.ConnectException
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
            batchSize = varselInstruksDAO.withConnection { connection ->
                try {
                    val pendingVarsler = varselInstruksDAO.getPendingForPublish(connection, PUBLISH_VARSEL_BATCH_LIMIT)

                    pendingVarsler.forEach { view ->
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

                    connection.commit()
                    pendingVarsler.size
                } catch (exception: Exception) {
                    connection.rollback()
                    throw exception
                }
            }
        } while (batchSize >= PUBLISH_VARSEL_BATCH_LIMIT)
    }
}

private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()
