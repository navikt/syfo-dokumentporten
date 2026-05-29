package no.nav.syfo.document.service

import dialogEntity
import document
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.syfo.TestDB
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.VarselInstruksStatus
import no.nav.syfo.document.db.exposed.VarselInstruksRepository
import varselInstruks

class DocumentServiceTest :
    DescribeSpec({
        val testDb = TestDB.database
        val exposedDb = TestDB.exposedDatabase
        val dialogDAO = DialogDAO(testDb)
        val documentDAO = DocumentDAO(testDb)
        val varselInstruksDAO = VarselInstruksRepository(exposedDb)
        val dialogService = mockk<DialogService>()
        val documentService = DocumentService(
            documentDAO = documentDAO,
            varselInstruksDAO = varselInstruksDAO,
            dialogService = dialogService,
            exposedDatabase = exposedDb,
            publicIngressUrl = "https://test.nav.no",
        )

        beforeTest {
            TestDB.clearAllData()
            clearAllMocks()
        }

        describe("insertDocument") {
            it("should store varsel instruks without publishing immediately") {
                runTest {
                    val existingDialog = dialogDAO.insertDialog(dialogEntity())
                    val incomingDocument = document(
                        varselInstruks = varselInstruks(),
                    ).copy(
                        fnr = existingDialog.fnr,
                        orgNumber = existingDialog.orgNumber,
                    )
                    coEvery {
                        dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                            incomingDocument.fnr,
                            incomingDocument.orgNumber,
                        )
                    } returns existingDialog

                    documentService.insertDocument(incomingDocument)

                    val persistedDocument = documentDAO.findDocumentsByParameters(
                        orgnumber = incomingDocument.orgNumber,
                        type = incomingDocument.type,
                        pageSize = 10,
                    ).items.single { it.documentId == incomingDocument.documentId }
                    val storedVarselInstruks = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    storedVarselInstruks?.status shouldBe VarselInstruksStatus.PENDING
                    storedVarselInstruks?.publishAttempts shouldBe 0
                    storedVarselInstruks?.publishedAt shouldBe null
                    storedVarselInstruks?.lastPublishError shouldBe null
                    storedVarselInstruks?.varselTekst shouldBe
                        incomingDocument.varselInstruks?.notifikasjonInnhold?.varselTekst?.trim()
                    coVerify(exactly = 1) {
                        dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                            incomingDocument.fnr,
                            incomingDocument.orgNumber,
                        )
                    }
                }
            }
        }
    })
