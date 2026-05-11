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
import no.nav.syfo.document.api.v1.dto.ESYFOVARSEL_KILDE_PREFIX
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.document.db.VarselInstruksDAO
import no.nav.syfo.document.db.VarselInstruksStatus
import no.nav.syfo.esyfovarsel.ArbeidsgiverNotifikasjonTilAltinnRessursHendelse
import no.nav.syfo.esyfovarsel.EsyfovarselHendelse
import no.nav.syfo.esyfovarsel.IEsyfovarselProducer
import no.nav.syfo.esyfovarsel.VarselPublishService
import org.apache.kafka.common.errors.TimeoutException
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import setVarselCreatedAt
import varselInstruks
import java.sql.Connection
import java.time.Instant
import java.time.temporal.ChronoUnit

class VarselPublishServiceTest :
    DescribeSpec({
        val testDb = TestDB.database
        val exposedDb = TestDB.exposedDatabase
        val dialogDAO = DialogDAO(testDb)
        val varselInstruksDAO = VarselInstruksDAO(exposedDb)
        val documentDAO = DocumentDAO(testDb)
        val esyfovarselProducer = mockk<IEsyfovarselProducer>()
        val varselPublishService = VarselPublishService(varselInstruksDAO, esyfovarselProducer, exposedDb)

        suspend fun insertDocumentWithVarsel(
            document: no.nav.syfo.document.api.v1.dto.Document,
            dialog: no.nav.syfo.document.db.PersistedDialogEntity,
        ): no.nav.syfo.document.db.PersistedDocumentEntity = suspendTransaction(db = exposedDb) {
            val persistedDocument = documentDAO.insert(
                connection.connection as Connection,
                document.toDocumentEntity(dialog),
                "test".toByteArray(),
            )
            if (document.varselInstruks != null) {
                varselInstruksDAO.insert(
                    documentId = persistedDocument.id,
                    ressursId = persistedDocument.type.altinnResource!!,
                    ressursUrl = "https://test.nav.no/api/v1/gui/documents/${persistedDocument.linkId}",
                    varselInstruks = document.varselInstruks,
                )
            }
            persistedDocument
        }

        beforeTest {
            TestDB.clearAllData()
            clearAllMocks()
        }

        describe("publishPendingVarsler") {
            it("should publish pending varsel and mark it as published") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val document = document(varselInstruks = varselInstruks())
                    val persistedDocument = insertDocumentWithVarsel(
                        document,
                        dialog,
                    )
                    val hendelseSlot = slot<EsyfovarselHendelse>()
                    every {
                        esyfovarselProducer.publish(
                            key = persistedDocument.documentId.toString(),
                            hendelse = capture(hendelseSlot),
                        )
                    } returns Unit
                    setVarselCreatedAt(
                        exposedDb = exposedDb,
                        documentId = persistedDocument.id,
                        created = Instant.now().minus(2, ChronoUnit.MINUTES),
                    )

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
                    hendelse.kilde shouldBe "$ESYFOVARSEL_KILDE_PREFIX${document.varselInstruks!!.kilde}"
                }
            }

            it("should keep varsel pending when producer fails with transient error") {
                runTest {
                    val dialog = dialogDAO.insertDialog(dialogEntity())
                    val document = document(varselInstruks = varselInstruks())
                    val persistedDocument = insertDocumentWithVarsel(
                        document,
                        dialog,
                    )
                    every {
                        esyfovarselProducer.publish(any(), any())
                    } throws TimeoutException("Timed out")
                    setVarselCreatedAt(
                        exposedDb = exposedDb,
                        documentId = persistedDocument.id,
                        created = Instant.now().minus(2, ChronoUnit.MINUTES),
                    )

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
                    val persistedDocument = insertDocumentWithVarsel(
                        document,
                        dialog,
                    )
                    val insertedVarselInstruks = varselInstruksDAO.getByDocumentId(persistedDocument.id)!!

                    repeat(9) {
                        suspendTransaction(db = exposedDb) {
                            varselInstruksDAO.markPublishError(
                                id = insertedVarselInstruks.id,
                                error = "Earlier data error",
                                isPermanentError = true,
                            )
                        }
                    }

                    every {
                        esyfovarselProducer.publish(any(), any())
                    } throws IllegalArgumentException("Invalid payload")
                    setVarselCreatedAt(
                        exposedDb = exposedDb,
                        documentId = persistedDocument.id,
                        created = Instant.now().minus(2, ChronoUnit.MINUTES),
                    )

                    varselPublishService.publishPendingVarsler()

                    val updated = varselInstruksDAO.getByDocumentId(persistedDocument.id)

                    updated?.status shouldBe VarselInstruksStatus.ERROR
                    updated?.publishAttempts shouldBe 10
                    updated?.lastPublishError shouldBe "Invalid payload"
                }
            }
        }
    })
