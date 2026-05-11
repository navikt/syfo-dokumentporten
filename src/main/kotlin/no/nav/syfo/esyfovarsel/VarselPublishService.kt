package no.nav.syfo.esyfovarsel

import kotlinx.coroutines.delay
import no.nav.syfo.document.api.v1.dto.ESYFOVARSEL_KILDE_PREFIX
import no.nav.syfo.document.db.VarselInstruksDAO
import no.nav.syfo.document.db.VarselInstruksPublishView
import no.nav.syfo.document.db.VarselInstruksStatus
import no.nav.syfo.util.logger
import org.apache.kafka.common.errors.SerializationException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant

private const val PUBLISH_VARSEL_BATCH_LIMIT = 100
private const val BACKOFF_DELAY_MS = 10_000L
const val MAX_FAILED_PUBLISH_ATTEMPTS = 10

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
        ).also {
            // TODO: Create alert
            // Use db state to account for max retry attempts as well
            if (it?.status == VarselInstruksStatus.ERROR) {
                COUNT_VARSEL_PERMANENT_ERROR.increment()
            }
        }
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
        copy(kilde = "$ESYFOVARSEL_KILDE_PREFIX$kilde")
}

private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()
