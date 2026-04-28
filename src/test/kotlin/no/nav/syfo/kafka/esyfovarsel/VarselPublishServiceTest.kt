package no.nav.syfo.kafka.esyfovarsel

import dialogEntity
import document
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.syfo.TestDB
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.VarselInstruksDAO
import no.nav.syfo.document.db.VarselInstruksStatus
import org.apache.kafka.common.errors.TimeoutException
import varselInstruks

class VarselPublishServiceTest :
    DescribeSpec({
        val testDb = TestDB.database
        val dialogDAO = DialogDAO(testDb)
        val varselInstruksDAO = VarselInstruksDAO(testDb)
        val documentDAO = DocumentDAO(testDb, varselInstruksDAO)
        val esyfovarselProducer = mockk<IEsyfovarselProducer>()
        val varselPublishService = VarselPublishService(varselInstruksDAO, esyfovarselProducer)

        beforeTest {
            TestDB.clearAllData()
            clearAllMocks()
        }

        describe("publishPendingVarsler") {
            it("should publish pending varsel and mark it as published") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val document = document(varselInstruks = varselInstruks())
                    val persistedDocument = documentDAO.insert(
                        document.toDocumentEntity(dialog),
                        "test".toByteArray(),
                        document.varselInstruks,
                    )
                    val hendelseSlot = slot<EsyfovarselHendelse>()
                    every {
                        esyfovarselProducer.publish(
                            key = persistedDocument.documentId.toString(),
                            hendelse = capture(hendelseSlot),
                        )
                    } returns Unit

                    varselPublishService.publishPendingVarsler()

                    val updated = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    updated?.status shouldBe VarselInstruksStatus.PUBLISHED
                    updated?.publishAttempts shouldBe 1
                    updated?.lastPublishError shouldBe null
                    verify(exactly = 1) {
                        esyfovarselProducer.publish(
                            key = persistedDocument.documentId.toString(),
                            hendelse = any(),
                        )
                    }
                    val hendelse = hendelseSlot.captured as ArbeidsgiverNotifikasjonTilAltinnRessursHendelse
                    hendelse.eksternReferanseId shouldBe persistedDocument.documentId.toString()
                    hendelse.orgnummer shouldBe dialog.orgNumber
                    hendelse.arbeidstakerFnr shouldBe dialog.fnr
                }
            }

            it("should keep varsel pending when producer fails with infra error") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val document = document(varselInstruks = varselInstruks())
                    val persistedDocument = documentDAO.insert(
                        document.toDocumentEntity(dialog),
                        "test".toByteArray(),
                        document.varselInstruks,
                    )
                    every {
                        esyfovarselProducer.publish(any(), any())
                    } throws TimeoutException("Timed out")

                    varselPublishService.publishPendingVarsler()

                    val updated = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    updated?.status shouldBe VarselInstruksStatus.PENDING
                    updated?.publishAttempts shouldBe 1
                    updated?.lastPublishError shouldBe "Timed out"
                }
            }

            it("should mark varsel as error after tenth data error") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val document = document(varselInstruks = varselInstruks())
                    val persistedDocument = documentDAO.insert(
                        document.toDocumentEntity(dialog),
                        "test".toByteArray(),
                        document.varselInstruks,
                    )
                    val insertedVarselInstruks = varselInstruksDAO.getByDocumentId(persistedDocument.id)!!

                    repeat(9) {
                        testDb.connection.use { connection ->
                            varselInstruksDAO.markPublishError(
                                connection = connection,
                                id = insertedVarselInstruks.id,
                                error = "Earlier data error",
                                isInfraError = false,
                            )
                            connection.commit()
                        }
                    }

                    every {
                        esyfovarselProducer.publish(any(), any())
                    } throws IllegalArgumentException("Invalid payload")

                    varselPublishService.publishPendingVarsler()

                    val updated = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    updated?.status shouldBe VarselInstruksStatus.ERROR
                    updated?.publishAttempts shouldBe 10
                    updated?.lastPublishError shouldBe "Invalid payload"
                }
            }
        }
    })
