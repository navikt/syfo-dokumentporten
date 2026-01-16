package no.nav.syfo.altinn.dialogporten.service

import com.fasterxml.uuid.Generators
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import no.nav.syfo.API_V1_PATH
import no.nav.syfo.DOCUMENT_API_PATH
import no.nav.syfo.GUI_DOCUMENT_API_PATH
import no.nav.syfo.altinn.dialogporten.client.IDialogportenClient
import no.nav.syfo.altinn.dialogporten.domain.Attachment
import no.nav.syfo.altinn.dialogporten.domain.AttachmentUrlConsumerType
import no.nav.syfo.altinn.dialogporten.domain.Content
import no.nav.syfo.altinn.dialogporten.domain.ContentValueItem
import no.nav.syfo.altinn.dialogporten.domain.Dialog
import no.nav.syfo.altinn.dialogporten.domain.Transmission
import no.nav.syfo.altinn.dialogporten.domain.Url
import no.nav.syfo.altinn.dialogporten.domain.create
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.DocumentEntity
import no.nav.syfo.document.db.DocumentStatus
import no.nav.syfo.document.db.PersistedDocumentEntity
import no.nav.syfo.util.logger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import no.nav.syfo.altinn.dialogporten.COUNT_DIALOGPORTEN_DIALOGS_CREATED
import no.nav.syfo.altinn.dialogporten.COUNT_DIALOGPORTEN_TRANSMISSIONS_CREATED
import no.nav.syfo.document.db.DialogDAO

const val DIALOG_RESSURS = "nav_syfo_dialog"

class DialogportenService(
    private val dialogportenClient: IDialogportenClient,
    private val documentDAO: DocumentDAO,
    private val publicIngressUrl: String,
    private val dialogportenIsApiOnly: Boolean,
    private val dialogDAO: DialogDAO,
) {
    private val logger = logger()
    private val deleteDialogLimit = 100
    private val sendDialogLimit = 100
    suspend fun sendDocumentsToDialogporten() {
        do {
            val documentsToSend = getDocumentsToSend()
            logger.info("Found ${documentsToSend.size} documents to send to dialogporten")

            val newDialogs = mutableMapOf<Long, UUID>()
            for (document in documentsToSend) {
                try {
                    val dialogportenId = document.dialog.dialogportenUUID
                        ?: newDialogs[document.dialog.id]

                    if (dialogportenId != null) {
                        addToExistingDialog(document, dialogportenId)
                    } else {
                        val dialogportenId = addToNewDialog(document)
                        newDialogs[document.dialog.id] = dialogportenId
                    }
                } catch (ex: Exception) {
                    logger.error("Failed to send document ${document.id} to dialogporten", ex)
                }
            }
            delay(5000L) // small delay to avoid hammering dialogporten
        } while (documentsToSend.size >= sendDialogLimit)
    }

    suspend fun deleteDialogsInDialogporten() {
        do {
            val dialogsToDeleteInDialogporten = getDocumentsToDelete()
            logger.info("Found ${dialogsToDeleteInDialogporten.size} documents to delete from to dialogporten")

            for (dialog in dialogsToDeleteInDialogporten) {
                try {

                    dialog.dialogportenUUID?.let { uuid ->
                        val status = dialogportenClient.deleteDialog(uuid)
                        if (status.isSuccess()) {
                            dialogDAO.updateDialogportenAfterDelete(
                                dialog.copy(
                                    dialogportenUUID = null,
                                    updated = Instant.now()
                                )
                            )
                        } else if (listOf(HttpStatusCode.Gone, HttpStatusCode.NotFound).contains(status)) {
                            logger.info("Skipping setting properties to null, dialog ${dialog.id} with dialogportenUUID $uuid already deleted in dialogporten")
                            dialogDAO.updateDialogportenAfterDelete(
                                dialog.copy(
                                    updated = Instant.now()
                                )
                            )
                        } else {
                            logger.error("Failed to delete dialog ${dialog.id} with dialogportenUUID $uuid from dialogporten, received status $status")
                            throw RuntimeException("Failed to delete dialog ${dialog.id} with dialogportenUUID $uuid")
                        }
                    }
                } catch (ex: Exception) {
                    logger.error("Failed to delete dialog ${dialog.id} from dialogporten", ex)
                    throw ex
                }
            }
            delay(5000L) // small delay to avoid hammering dialogporten
        } while (dialogsToDeleteInDialogporten.size >= deleteDialogLimit)
    }

    private suspend fun addToExistingDialog(document: PersistedDocumentEntity, dialogportenId: UUID) {
        val transmissionId = Generators.timeBasedEpochGenerator().generate()
        val transmission = document.toTransmission(transmissionId = transmissionId)
        dialogportenClient.addTransmission(transmission, dialogportenId)
        documentDAO.update(
            document.copy(
                transmissionId = transmissionId,
                status = DocumentStatus.COMPLETED,
                updated = Instant.now()
            )
        )
        val fullDocumentLink = createApiDocumentLink(document.linkId.toString())
        COUNT_DIALOGPORTEN_TRANSMISSIONS_CREATED.increment()
        logger.info("Added transmission $transmissionId for document ${document.id}, dialogportenId $dialogportenId, with link $fullDocumentLink and content type ${document.contentType}")
    }

    private suspend fun addToNewDialog(document: PersistedDocumentEntity): UUID {
        val transmissionId = Generators.timeBasedEpochGenerator().generate()
        val dialog = document.toDialogWithTransmission(transmissionId)
        val dialogId = dialogportenClient.createDialog(dialog)
        documentDAO.update(
            document.copy(
                transmissionId = transmissionId,
                status = DocumentStatus.COMPLETED,
                updated = Instant.now(),
                dialog = document.dialog.copy(
                    dialogportenUUID = dialogId,
                    updated = Instant.now()
                ),
            )
        )
        val fullDocumentLink = createApiDocumentLink(document.linkId.toString())
        COUNT_DIALOGPORTEN_DIALOGS_CREATED.increment()
        COUNT_DIALOGPORTEN_TRANSMISSIONS_CREATED.increment()
        logger.info("Create dialog $dialogId, with transmission $transmissionId for document ${document.id}, with link $fullDocumentLink and content type ${document.contentType}")
        return dialogId
    }

    private suspend fun getDocumentsToSend() = documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED, 100)
    private suspend fun getDocumentsToDelete() =
        dialogDAO.getDialogAwaitingDeletionInDialogporten(limit = deleteDialogLimit)

    private fun createApiDocumentLink(linkId: String): String =
        "$publicIngressUrl$API_V1_PATH$DOCUMENT_API_PATH/$linkId"

    private fun createGuiDocumentLink(linkId: String): String =
        "$publicIngressUrl$API_V1_PATH$GUI_DOCUMENT_API_PATH/$linkId"

    private fun getDocumentDisplayName(document: DocumentEntity): String {
        val fileType = when (document.contentType) {
            "application/pdf" -> "pdf"
            "application/json" -> "json"
            else -> throw IllegalArgumentException("Unsupported document content type ${document.contentType}")
        }
        return "${document.type.displayName}.$fileType"
    }

    private fun DocumentEntity.toDialogWithTransmission(transmissionId: UUID): Dialog {
        return Dialog(
            serviceResource = "urn:altinn:resource:$DIALOG_RESSURS",
            party = "urn:altinn:organization:identifier-no:${dialog.orgNumber}",
            externalReference = "syfo-dokumentporten",
            content = Content.create(
                title = dialog.title,
                summary = dialog.summary,
            ),
            isApiOnly = dialogportenIsApiOnly,
            transmissions = listOf(
                toTransmission(transmissionId)
            )
        )
    }

    private fun DocumentEntity.toTransmission(transmissionId: UUID): Transmission {
        return Transmission(
            id = transmissionId,
            content = Content.create(
                title = title,
                summary = summary,
            ),
            type = Transmission.TransmissionType.Information,
            sender = Transmission.Sender("ServiceOwner"),
            externalReference = documentId.toString(),
            extendedType = type.name,
            attachments = listOf(
                Attachment(
                    displayName = listOf(
                        ContentValueItem(
                            getDocumentDisplayName(this),
                            "nb"
                        ),
                    ),
                    urls = listOf(
                        Url(
                            url = createApiDocumentLink(linkId.toString()),
                            mediaType = contentType,
                            consumerType = AttachmentUrlConsumerType.Api,
                        ),
                        Url(
                            url = createGuiDocumentLink(linkId.toString()),
                            mediaType = contentType,
                            consumerType = AttachmentUrlConsumerType.Gui,
                        ),
                    ),
                    expiresAt = instantStartOfFollowingDay4MonthsAhead()
                ),
            ),
        )
    }

    private fun instantStartOfFollowingDay4MonthsAhead(): Instant {
        return LocalDate.now()
            .plusMonths(4)
            .plusDays(1)
            .atTime(LocalTime.MIN)
            .atZone(ZoneId.systemDefault())
            .toInstant()
    }
}
