package no.nav.syfo.document.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.API_V1_PATH
import no.nav.syfo.GUI_DOCUMENT_API_PATH
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.document.api.v1.COUNT_DOCUMENT_RECIEVED
import no.nav.syfo.document.api.v1.COUNT_VARSEL_INSTRUKS_RECEIVED
import no.nav.syfo.document.api.v1.dto.Document
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.document.api.v1.dto.validate
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.DocumentInsertException
import no.nav.syfo.document.db.VarselInstruksDAO
import no.nav.syfo.util.logger

class DocumentService(
    private val documentDAO: DocumentDAO,
    private val varselInstruksDAO: VarselInstruksDAO,
    private val dialogService: DialogService,
    private val database: DatabaseInterface,
    private val publicIngressUrl: String,
) {
    private val logger = logger()

    suspend fun insertDocument(document: Document) {
        if (document.varselInstruks != null && document.type != DocumentType.DIALOGMOTE) {
            throw ApiErrorException.BadRequestException(
                "varselInstruks er kun støttet for dokumenttype DIALOGMOTE (mottok ${document.type})"
            )
        }

        document.varselInstruks?.validate()

        val existingDialog = dialogService.getAndUpdateDialogByFnrAndOrgNumber(document.fnr, document.orgNumber)
            ?: dialogService.insertDialog(document)

        val documentEntity = document.toDocumentEntity(existingDialog)

        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                runCatching {
                    val insertedDocument = documentDAO.insert(connection, documentEntity, document.content)

                    if (document.varselInstruks != null) {
                        val altinnResource = documentEntity.type.altinnResource
                            ?: throw DocumentInsertException(
                                "varselInstruks er kun støttet for dokumenttyper med en Altinn-ressurs (type=${documentEntity.type})"
                            )

                        val ressursUrl = createGuiDocumentLink(insertedDocument.linkId.toString())

                        varselInstruksDAO.insert(
                            connection = connection,
                            documentId = insertedDocument.id,
                            ressursId = altinnResource,
                            ressursUrl = ressursUrl,
                            varselInstruks = document.varselInstruks,
                        )
                    }

                    connection.commit()
                }.onFailure { ex ->
                    connection.rollback()
                    logger.error("Failed to insert document: ${ex.message}", ex)
                    throw ApiErrorException.InternalServerErrorException("Failed to insert document")
                }
            }
        }

        if (document.varselInstruks != null) {
            COUNT_VARSEL_INSTRUKS_RECEIVED.increment()
        }
        COUNT_DOCUMENT_RECIEVED.increment()
    }

    private fun createGuiDocumentLink(linkId: String): String =
        "$publicIngressUrl$API_V1_PATH$GUI_DOCUMENT_API_PATH/$linkId"
}
