package no.nav.syfo.dialogporten.service

import dialogEntity
import documentEntity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import no.nav.syfo.altinn.dialogporten.client.IDialogportenClient
import no.nav.syfo.altinn.dialogporten.domain.Dialog
import no.nav.syfo.altinn.dialogporten.domain.Transmission
import no.nav.syfo.altinn.dialogporten.service.DialogportenService
import no.nav.syfo.document.api.v1.dto.DocumentType
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.DocumentStatus
import java.util.UUID

class DialogportenServiceTest : DescribeSpec({
    val dialogportenClient = mockk<IDialogportenClient>()
    val documentDAO = mockk<DocumentDAO>()
    val publicIngressUrl = "https://test.nav.no"

    val dialogportenService = DialogportenService(
        dialogportenClient = dialogportenClient,
        documentDAO = documentDAO,
        publicIngressUrl = publicIngressUrl,
        dialogportenIsApiOnly = true
    )

    beforeTest {
        clearAllMocks()
    }

    describe("sendDocumentsToDialogporten") {
        context("when there are no documents to send") {
            it("should not call dialogporten client") {
                // Arrange
                coEvery { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) } returns emptyList()

                // Act
                dialogportenService.sendDocumentsToDialogporten()

                // Assert
                coVerify(exactly = 1) { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) }
                coVerify(exactly = 0) { dialogportenClient.createDialog(any()) }
                coVerify(exactly = 0) { documentDAO.update(any()) }
            }
        }

        context("when there is one document to send") {
            it("should send document to dialogporten with createDialog and update status to COMPLETED") {
                // Arrange (no existing dialog, dialogportenId = null)
                val dialogEntity = dialogEntity().copy(dialogportenUUID = null)
                val documentEntity = documentEntity(dialogEntity)
                val dialogId = UUID.randomUUID()
                val dialogSlot = slot<Dialog>()

                coEvery { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) } returns listOf(documentEntity)
                coEvery { dialogportenClient.createDialog(capture(dialogSlot)) } returns dialogId
                coEvery { documentDAO.update(any()) } returns Unit

                // Act
                dialogportenService.sendDocumentsToDialogporten()

                // Assert
                coVerify(exactly = 1) { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) }
                coVerify(exactly = 1) { dialogportenClient.createDialog(any()) }
                coVerify(exactly = 1) {
                    documentDAO.update(match {
                        it.dialog.dialogportenUUID == dialogId
                                && it.status == DocumentStatus.COMPLETED
                                && it.transmissionId == dialogSlot.captured.transmissions.first().id
                    })
                }

                val capturedDialog = dialogSlot.captured
                capturedDialog.party shouldBe "urn:altinn:organization:identifier-no:${documentEntity.dialog.orgNumber}"
                capturedDialog.externalReference shouldBe "syfo-dokumentporten"
                capturedDialog.content.title.value.first().value shouldBe dialogEntity.title
                capturedDialog.content.summary?.value?.first()?.value shouldBe dialogEntity.summary
                capturedDialog.isApiOnly shouldBe true
                capturedDialog.attachments shouldBe emptyList()
                capturedDialog.transmissions.size shouldBe 1
                capturedDialog.transmissions.first().externalReference shouldBe documentEntity.documentId.toString()
                capturedDialog.transmissions.first().attachments.first()
                    .displayName.first().value shouldBe "${documentEntity.type.displayName}.pdf"
            }

            it("should send document to dialogporten with addTransmission and update status to COMPLETED") {
                // Arrange (already existing dialog)
                val dialogEntity = dialogEntity().copy(dialogportenUUID = UUID.randomUUID())
                val documentEntity = documentEntity(dialogEntity)
                val transmissionSlot = slot<Transmission>()

                coEvery { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) } returns listOf(documentEntity)
                coEvery { dialogportenClient.addTransmission(capture(transmissionSlot), any()) } returns UUID.randomUUID()
                coEvery { documentDAO.update(any()) } returns Unit

                // Act
                dialogportenService.sendDocumentsToDialogporten()

                // Assert
                coVerify(exactly = 1) { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) }
                coVerify(exactly = 0) { dialogportenClient.createDialog(any()) }
                coVerify(exactly = 1) { dialogportenClient.addTransmission(any(), dialogEntity.dialogportenUUID!!) }
                coVerify(exactly = 1) {
                    documentDAO.update(match {
                        it.dialog.dialogportenUUID == dialogEntity.dialogportenUUID
                                && it.status == DocumentStatus.COMPLETED
                                && it.transmissionId == transmissionSlot.captured.id
                    })
                }

                val capturedTransmission = transmissionSlot.captured
                capturedTransmission.id shouldNotBe null
                capturedTransmission.externalReference shouldBe documentEntity.documentId.toString()
                capturedTransmission.content.title.value.first().value shouldBe documentEntity.title
                capturedTransmission.content.summary?.value?.first()?.value shouldBe documentEntity.summary
                capturedTransmission.attachments.size shouldBe 1
                capturedTransmission.attachments.first()
                    .displayName.first().value shouldBe "${documentEntity.type.displayName}.pdf"
            }
        }

        context("when there are multiple documents to send") {
            it("should send all documents to dialogporten") {
                // Arrange
                val doc1 = documentEntity(dialogEntity().copy(dialogportenUUID = null))
                val doc2 = documentEntity(dialogEntity().copy(dialogportenUUID = null))
                    .copy(type = DocumentType.OPPFOLGINGSPLAN)
                val dialogId1 = UUID.randomUUID()
                val dialogId2 = UUID.randomUUID()

                coEvery { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) } returns listOf(doc1, doc2)
                coEvery { dialogportenClient.createDialog(any()) } returnsMany listOf(dialogId1, dialogId2)
                coEvery { documentDAO.update(any()) } returns Unit

                // Act
                dialogportenService.sendDocumentsToDialogporten()

                // Assert
                coVerify(exactly = 1) { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) }
                coVerify(exactly = 2) { dialogportenClient.createDialog(any()) }
                coVerify(exactly = 2) { documentDAO.update(any()) }
            }
        }

        context("when dialogporten client throws exception") {
            it("should log error and continue without updating document status") {
                // Arrange
                val documentEntity = documentEntity(dialogEntity().copy(dialogportenUUID = null))
                coEvery { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) } returns listOf(documentEntity)
                coEvery { dialogportenClient.createDialog(any()) } throws RuntimeException("Dialogporten error")

                // Act
                dialogportenService.sendDocumentsToDialogporten()

                // Assert
                coVerify(exactly = 1) { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) }
                coVerify(exactly = 1) { dialogportenClient.createDialog(any()) }
                coVerify(exactly = 0) { documentDAO.update(any()) }
            }
        }

        context("when one document fails but others succeed") {
            it("should continue processing remaining documents") {
                // Arrange
                val doc1 = documentEntity(dialogEntity().copy(dialogportenUUID = null))
                val doc2 = documentEntity(dialogEntity().copy(dialogportenUUID = null))
                val doc3 = documentEntity(dialogEntity().copy(dialogportenUUID = null))
                val dialogId2 = UUID.randomUUID()
                val dialogId3 = UUID.randomUUID()

                coEvery { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) } returns listOf(doc1, doc2, doc3)
                coEvery { documentDAO.update(any()) } returns Unit

                // First call succeeds, second fails, third succeeds
                var callCount = 0
                coEvery { dialogportenClient.createDialog(any()) } answers {
                    callCount++
                    when (callCount) {
                        1 -> dialogId2
                        2 -> throw RuntimeException("Error")
                        3 -> dialogId3
                        else -> throw RuntimeException("Unexpected call")
                    }
                }

                // Act
                dialogportenService.sendDocumentsToDialogporten()

                // Assert
                coVerify(exactly = 1) { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) }
                coVerify(exactly = 3) { dialogportenClient.createDialog(any()) }
                coVerify(exactly = 2) { documentDAO.update(any()) } // Only 2 successful updates
            }
        }

        context("when document has JSON content type") {
            it("should create dialog with correct display name") {
                // Arrange
                val documentEntity = documentEntity(dialogEntity().copy(dialogportenUUID = null))
                    .copy(contentType = "application/json")
                val dialogId = UUID.randomUUID()
                val dialogSlot = slot<Dialog>()

                coEvery { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) } returns listOf(documentEntity)
                coEvery { dialogportenClient.createDialog(capture(dialogSlot)) } returns dialogId
                coEvery { documentDAO.update(any()) } returns Unit

                // Act
                dialogportenService.sendDocumentsToDialogporten()

                // Assert
                val capturedDialog = dialogSlot.captured
                capturedDialog.transmissions.first()
                    .attachments.first()
                    .displayName.first().value shouldBe "${documentEntity.type.displayName}.json"
            }
        }

        context("when document content includes correct resource URN") {
            it("should use nav_syfo_dialog resource") {
                // Arrange
                val documentEntity = documentEntity(dialogEntity().copy(dialogportenUUID = null))
                val dialogId = UUID.randomUUID()
                val dialogSlot = slot<Dialog>()

                coEvery { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) } returns listOf(documentEntity)
                coEvery { dialogportenClient.createDialog(capture(dialogSlot)) } returns dialogId
                coEvery { documentDAO.update(any()) } returns Unit

                // Act
                dialogportenService.sendDocumentsToDialogporten()

                // Assert
                val capturedDialog = dialogSlot.captured
                capturedDialog.serviceResource shouldBe "urn:altinn:resource:nav_syfo_dialog"
            }
        }

        context("when document has attachment URL") {
            it("should create correct document link with linkId") {
                // Arrange
                val documentEntity = documentEntity(dialogEntity().copy(dialogportenUUID = null))
                val dialogId = UUID.randomUUID()
                val dialogSlot = slot<Dialog>()

                coEvery { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) } returns listOf(documentEntity)
                coEvery { dialogportenClient.createDialog(capture(dialogSlot)) } returns dialogId
                coEvery { documentDAO.update(any()) } returns Unit

                // Act
                dialogportenService.sendDocumentsToDialogporten()

                // Assert
                val capturedDialog = dialogSlot.captured
                val attachmentUrl = capturedDialog.transmissions.first().attachments.first().urls.first().url
                attachmentUrl shouldBe "$publicIngressUrl/api/v1/documents/${documentEntity.linkId}"
            }
        }

        context("when 3 documents belongs to same dialog") {
            it("should create 1 dialog and add 2 transmissions") {
                // Arrange
                val dialogEntity = dialogEntity().copy(
                    dialogportenUUID = null
                )
                val doc1 = documentEntity(dialogEntity)
                val doc2 = documentEntity(dialogEntity)
                val doc3 = documentEntity(dialogEntity)

                coEvery { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) } returns listOf(doc1, doc2, doc3)
                coEvery { documentDAO.update(any()) } returns Unit
                val returnedDialogId = UUID.randomUUID()
                coEvery { dialogportenClient.createDialog(any()) } returns returnedDialogId
                coEvery { dialogportenClient.addTransmission(any(), any()) } returns UUID.randomUUID()

                // Act
                dialogportenService.sendDocumentsToDialogporten()

                // Assert
                coVerify(exactly = 1) { documentDAO.getDocumentsByStatus(DocumentStatus.RECEIVED) }
                coVerify(exactly = 1) { dialogportenClient.createDialog(any()) }
                coVerify(exactly = 2) { dialogportenClient.addTransmission(any(), returnedDialogId) }
                coVerify(exactly = 3) { documentDAO.update(any()) }
            }
        }
    }
})
