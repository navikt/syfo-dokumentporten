package no.nav.syfo.esyfovarsel

import kotlinx.coroutines.delay
import no.nav.syfo.document.db.VarselInstruksDAO
import no.nav.syfo.document.db.VarselInstruksPublishView
import no.nav.syfo.util.logger
import org.apache.kafka.common.errors.SerializationException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant

private const val PUBLISH_VARSEL_BATCH_LIMIT = 100
private const val BACKOFF_DELAY_MS = 10_000L

class VarselPublishService(
    private val varselInstruksDAO: VarselInstruksDAO,
    private val esyfovarselProducer: IEsyfovarselProducer,
    private val exposedDatabase: Database,
) {
    private val logger = logger()

    suspend fun publishPendingVarsler() {
        var batchSize: Int
        var failedInBatch: Int

        do {
            val pendingVarsler = varselInstruksDAO.getPendingForPublish(PUBLISH_VARSEL_BATCH_LIMIT)
            batchSize = pendingVarsler.size
            failedInBatch = 0

            pendingVarsler.forEach { view ->
                try {
                    publishPendingVarsel(view)
                } catch (exception: Exception) {
                    failedInBatch++
                    logger.error(
                        "Unexpected error publishing varsel_instruks ${view.id}, continuing with next",
                        exception
                    )
                }
            }

            if (failedInBatch > 0) {
                logger.warn("$failedInBatch of $batchSize varsel publiseringer feilet, pauser før neste batch")
                delay(BACKOFF_DELAY_MS)
            }
        } while (batchSize >= PUBLISH_VARSEL_BATCH_LIMIT)
    }

    private fun publishToKafka(view: VarselInstruksPublishView): ArbeidsgiverNotifikasjonTilAltinnRessursHendelse {
        val hendelse = view
            .toEsyfovarselHendelse()
            .prependDokumentportenToKilde()

        esyfovarselProducer.publish(
            key = view.documentId.toString(),
            hendelse = hendelse,
        )
        return hendelse
    }

    private fun markPublished(view: VarselInstruksPublishView) {
        varselInstruksDAO.markPublished(view.id, Instant.now())
        COUNT_VARSEL_PUBLISHED.increment()
    }

    private fun markFailed(view: VarselInstruksPublishView, exception: Exception) {
        val rootCause = exception.rootCause()
        val isPermanentError = rootCause is SerializationException ||
            rootCause is IllegalArgumentException ||
            rootCause is ClassCastException

        varselInstruksDAO.markPublishError(
            id = view.id,
            error = rootCause.message ?: "unknown",
            isPermanentError = isPermanentError,
        )
        COUNT_VARSEL_PUBLISH_FAILED.increment()
        logger.error(
            "Failed to publish varsel_instruks ${view.id} to esyfovarsel. permanentError=$isPermanentError",
            exception,
        )
    }

    private suspend fun publishPendingVarsel(view: VarselInstruksPublishView) {
        try {
            publishToKafka(view)
        } catch (exception: Exception) {
            suspendTransaction(db = exposedDatabase) {
                markFailed(view, exception)
            }
            return
        }

        suspendTransaction(db = exposedDatabase) {
            markPublished(view)
        }
    }

    private fun ArbeidsgiverNotifikasjonTilAltinnRessursHendelse.prependDokumentportenToKilde() =
        copy(kilde = "dokumentporten.$kilde")
}

private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()
