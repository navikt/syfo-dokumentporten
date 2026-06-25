package no.nav.syfo.altinn.dialogporten.service

import com.fasterxml.uuid.Generators
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import no.nav.syfo.API_V1_PATH
import no.nav.syfo.DOCUMENT_API_PATH
import no.nav.syfo.GUI_DOCUMENT_API_PATH
import no.nav.syfo.altinn.dialogporten.COUNT_DIALOGPORTEN_DIALOGS_CREATED
import no.nav.syfo.altinn.dialogporten.COUNT_DIALOGPORTEN_TRANSMISSIONS_CREATED
import no.nav.syfo.altinn.dialogporten.client.DialogportenClient
import no.nav.syfo.altinn.dialogporten.client.DialogportenClientException
import no.nav.syfo.altinn.dialogporten.client.IDialogportenClient
import no.nav.syfo.altinn.dialogporten.domain.Attachment
import no.nav.syfo.altinn.dialogporten.domain.AttachmentUrlConsumerType
import no.nav.syfo.altinn.dialogporten.domain.Content
import no.nav.syfo.altinn.dialogporten.domain.ContentValueItem
import no.nav.syfo.altinn.dialogporten.domain.Dialog
import no.nav.syfo.altinn.dialogporten.domain.ExtendedDialog
import no.nav.syfo.altinn.dialogporten.domain.Transmission
import no.nav.syfo.altinn.dialogporten.domain.Url
import no.nav.syfo.altinn.dialogporten.domain.create
import no.nav.syfo.document.api.v1.generateDialogTitle
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.DocumentEntity
import no.nav.syfo.document.db.DocumentStatus
import no.nav.syfo.document.db.PersistedDialogEntity
import no.nav.syfo.document.db.PersistedDocumentEntity
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.util.logger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

const val DIALOG_RESSURS = "nav_syfo_dialog"

class DialogportenService(
    private val dialogportenClient: IDialogportenClient,
    private val documentDAO: DocumentDAO,
    private val publicIngressUrl: String,
    private val dialogDAO: DialogDAO,
    private val pdlService: PdlService,
) {
    private val logger = logger()
    private val sendDialogLimit = 100

    suspend fun sendDocumentsToDialogporten() {
        var batchNum = 0
        do {
            val documentsToSend = getDocumentsToSend()
            batchNum += 1
            val firstCreatedTimestamp = if (documentsToSend.isNotEmpty()) {
                documentsToSend.first().created
            } else {
                null
            }
            logger.info(
                "Batch: $batchNum: Found ${documentsToSend.size} documents to send to dialogporten. First created at ${firstCreatedTimestamp ?: "N/A"}"
            )

            if (documentsToSend.isEmpty()) {
                break
            }

            val enrichedBirthDates = mutableMapOf<Long, LocalDate?>()
            val updatedDialogs = mutableMapOf<Long, PersistedDialogEntity>()
            val newDialogs = mutableMapOf<Long, UUID>()
            for (document in documentsToSend) {
                try {
                    val dialogId = document.dialog.id
                    if (dialogId !in enrichedBirthDates) {
                        val fodselsdato: LocalDate? = document.dialog.birthDate ?: run {
                            val personInfo = pdlService.getPersonInfo(document.dialog.fnr)
                            val birthDateString = personInfo.birthDate
                            if (birthDateString == null) {
                                logger.warn("Could not find fødselsdato for dialog $dialogId")
                                return@run null
                            }
                            val parsed = LocalDate.parse(birthDateString)
                            val nameOrFnr = personInfo.fullName ?: document.dialog.fnr
                            val newTitle = generateDialogTitle(nameOrFnr, document.dialog.fnr, parsed)
                            val updatedDialog = dialogDAO.updateDialogWithBirthDate(dialogId, parsed, newTitle)
                            updatedDialogs[dialogId] = updatedDialog
                            parsed
                        }
                        enrichedBirthDates[dialogId] = fodselsdato
                    }

                    val enrichedDocument = updatedDialogs[dialogId]?.let { updatedDialog ->
                        document.copy(dialog = updatedDialog)
                    } ?: document

                    val dialogportenId = enrichedDocument.dialog.dialogportenUUID
                        ?: newDialogs[dialogId]

                    if (dialogportenId != null) {
                        addToExistingDialog(enrichedDocument, dialogportenId)
                    } else {
                        val dialogportenIdNew = addToNewDialog(enrichedDocument)
                        newDialogs[dialogId] = dialogportenIdNew
                    }
                } catch (ex: Exception) {
                    logger.error("Failed to send document ${document.id} to dialogporten", ex)
                }
            }
        } while (documentsToSend.size >= sendDialogLimit)
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
        logger.info(
            "Added transmission $transmissionId for document ${document.id}, dialogportenId $dialogportenId, with link $fullDocumentLink and content type ${document.contentType}"
        )
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
        logger.info(
            "Create dialog $dialogId, with transmission $transmissionId for document ${document.id}, with link $fullDocumentLink and content type ${document.contentType}"
        )
        return dialogId
    }

    private suspend fun getDocumentsToSend() = documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED, 100)

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

    private fun DocumentEntity.toDialogWithTransmission(transmissionId: UUID): Dialog = Dialog(
        serviceResource = "urn:altinn:resource:$DIALOG_RESSURS",
        party = "urn:altinn:organization:identifier-no:${dialog.orgNumber}",
        externalReference = "syfo-dokumentporten",
        content = Content.create(
            title = dialog.title,
            summary = dialog.summary,
        ),
        isApiOnly = false,
        transmissions = listOf(
            toTransmission(transmissionId)
        )
    )

    private fun DocumentEntity.toTransmission(transmissionId: UUID): Transmission = Transmission(
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

    private fun instantStartOfFollowingDay4MonthsAhead(): Instant = LocalDate.now()
        .plusMonths(4)
        .plusDays(1)
        .atTime(LocalTime.MIN)
        .atZone(ZoneId.systemDefault())
        .toInstant()

    suspend fun updateApiOnlyForDialog() {
        do {
            val dialogsToUpdate =
                dialogDAO.getDialogCandidatesWithApiOnlyTrue()
            val batchResult = updateApiOnlyBatch(dialogsToUpdate)

            for (dialogId in batchResult.updatedDialogIds) {
                dialogDAO.setDialogApiOnlyFalse(dialogId)
            }

            if (batchResult.failedDialogIds.isNotEmpty() && batchResult.updatedDialogIds.isEmpty()) {
                logger.warn(
                    "Processing halted: received only failing dialogs (${batchResult.failedDialogIds.size}). Aborting..."
                )
                break
            }

            logger.info("Updated ${batchResult.updatedDialogIds.size} dialogs")
            logger.info("Failed to process ${batchResult.failedDialogIds.size} dialogs")
            delay(1000.milliseconds)
        } while (dialogsToUpdate.isNotEmpty())
    }

    private suspend fun updateApiOnlyBatch(dialogIds: List<UUID>): ApiOnlyBatchResult {
        val dialogUpdateResults = dialogIds
            .asFlow()
            .map(::getDialogForApiOnlyUpdate)
            .map(::patchDialogApiOnly)
            .toList()
        val updatedDialogIds = dialogUpdateResults
            .filterIsInstance<DialogApiOnlyUpdateResult.Updated>()
            .map { it.dialogId }
        val failedDialogIds = dialogUpdateResults
            .filterIsInstance<DialogApiOnlyUpdateResult.Failed>()
            .map { it.dialogId }
            .toSet()

        return ApiOnlyBatchResult(
            updatedDialogIds = updatedDialogIds,
            failedDialogIds = failedDialogIds,
        )
    }

    private suspend fun patchDialogApiOnly(dialogResult: DialogApiOnlyUpdateResult): DialogApiOnlyUpdateResult =
        when (dialogResult) {
            is DialogApiOnlyUpdateResult.Failed,
            is DialogApiOnlyUpdateResult.Updated -> dialogResult

            is DialogApiOnlyUpdateResult.ReadyForPatch -> {
                try {
                    if (dialogResult.dialog.isApiOnly) {
                        dialogportenClient.patchDialog(
                            dialogResult.dialog.id,
                            dialogResult.dialog.revision,
                            patch = listOf(
                                DialogportenClient.DialogportenPatch(
                                    DialogportenClient.DialogportenPatch.OPERATION.REPLACE,
                                    DialogportenClient.DialogportenPatch.PATH.IS_API_ONLY,
                                    "false"
                                )
                            )
                        )
                    }

                    DialogApiOnlyUpdateResult.Updated(dialogResult.dialog.id)
                } catch (_: DialogportenClientException) {
                    DialogApiOnlyUpdateResult.Failed(dialogResult.dialog.id)
                }
            }
        }

    private suspend fun getDialogForApiOnlyUpdate(dialogId: UUID): DialogApiOnlyUpdateResult = try {
        DialogApiOnlyUpdateResult.ReadyForPatch(dialogportenClient.getDialogById(dialogId))
    } catch (_: DialogportenClientException) {
        DialogApiOnlyUpdateResult.Failed(dialogId)
    }

    private data class ApiOnlyBatchResult(val updatedDialogIds: List<UUID>, val failedDialogIds: Set<UUID>)

    private sealed interface DialogApiOnlyUpdateResult {
        data class ReadyForPatch(val dialog: ExtendedDialog) : DialogApiOnlyUpdateResult

        data class Updated(val dialogId: UUID) : DialogApiOnlyUpdateResult

        data class Failed(val dialogId: UUID) : DialogApiOnlyUpdateResult
    }
}
