package no.nav.syfo.document.service

import dialogEntity
import document
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DialogEntity
import no.nav.syfo.document.db.PersistedDialogEntity
import no.nav.syfo.pdl.PdlPersonInfo
import no.nav.syfo.pdl.PdlService
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

class DialogServiceTest :
    DescribeSpec({
        val dialogDAO = mockk<DialogDAO>()
        val pdlService = mockk<PdlService>()
        val dialogService = DialogService(dialogDAO, pdlService)

        beforeTest {
            clearAllMocks()
            coEvery { pdlService.getPersonInfo(any()) } returns PdlPersonInfo(fullName = null, birthDate = null)
        }

        describe("getAndUpdateDialogByFnrAndOrgNumber") {
            context("when no dialog exists for fnr and orgNumber") {
                it("should return null") {
                    // Arrange
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns null

                    // Act
                    val result = dialogService.getAndUpdateDialogByFnrAndOrgNumber("12345678901", "123456789")

                    // Assert
                    result shouldBe null
                    coVerify(exactly = 1) { dialogDAO.getByFnrAndOrgNumber("12345678901", "123456789") }
                    coVerify(exactly = 0) { pdlService.getBirthDateFor(any()) }
                    coVerify(exactly = 0) { dialogDAO.updateDialogWithBirthDate(any(), any(), any()) }
                }
            }

            context("when dialog exists and already has birthDate") {
                it("should return dialog without calling PDL") {
                    // Arrange
                    val existingDialog = dialogEntity().copy(birthDate = LocalDate.of(1990, 1, 15))
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog

                    // Act
                    val result = dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                        existingDialog.fnr,
                        existingDialog.orgNumber,
                    )

                    // Assert
                    result shouldBe existingDialog
                    result?.birthDate shouldBe LocalDate.of(1990, 1, 15)
                    coVerify(exactly = 1) {
                        dialogDAO.getByFnrAndOrgNumber(existingDialog.fnr, existingDialog.orgNumber)
                    }
                    coVerify(exactly = 0) { pdlService.getBirthDateFor(any()) }
                    coVerify(exactly = 0) { dialogDAO.updateDialogWithBirthDate(any(), any(), any()) }
                }
            }

            context("when dialog exists without birthDate and PDL returns birthDate") {
                it("should update dialog with birthDate and return enriched dialog") {
                    // Arrange
                    val existingDialog = dialogEntity().copy(birthDate = null)
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { pdlService.getPersonInfo(existingDialog.fnr) } returns
                        PdlPersonInfo(fullName = "Test Person", birthDate = "1985-06-20")
                    coEvery { dialogDAO.updateDialogWithBirthDate(any(), any(), any()) } returns existingDialog.copy(
                        birthDate = LocalDate.of(1985, 6, 20),
                        title = "Sykefraværsoppfølging for Test Person (f. 20.06.1985)",
                    )

                    // Act
                    val result = dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                        existingDialog.fnr,
                        existingDialog.orgNumber,
                    )

                    // Assert
                    result shouldNotBe null
                    result?.birthDate shouldBe LocalDate.of(1985, 6, 20)
                    result?.id shouldBe existingDialog.id
                    coVerify(exactly = 1) { pdlService.getPersonInfo(existingDialog.fnr) }
                    coVerify(exactly = 1) {
                        dialogDAO.updateDialogWithBirthDate(existingDialog.id, LocalDate.of(1985, 6, 20), any())
                    }
                    coVerify(exactly = 0) { dialogDAO.getById(any()) }
                }
            }

            context("when dialog exists without birthDate and PDL returns null") {
                it("should return dialog without birthDate and not update") {
                    // Arrange
                    val existingDialog = dialogEntity().copy(birthDate = null)
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { pdlService.getPersonInfo(any()) } returns
                        PdlPersonInfo(fullName = null, birthDate = null)

                    // Act
                    val result = dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                        existingDialog.fnr,
                        existingDialog.orgNumber,
                    )

                    // Assert
                    result shouldBe existingDialog
                    result?.birthDate shouldBe null
                    coVerify(exactly = 1) { pdlService.getPersonInfo(existingDialog.fnr) }
                    coVerify(exactly = 0) { dialogDAO.updateDialogWithBirthDate(any(), any(), any()) }
                }
            }
        }

        describe("insertDialog") {
            context("when PDL returns a birthDate for a regular fnr") {
                it("should insert dialog with birthDate and correct title from PDL") {
                    // Arrange
                    val doc = document().copy(
                        fnr = "01031992000",
                        fullName = "Ola Nordmann",
                        orgNumber = "123456789",
                        birthDate = null,
                    )
                    val insertedSlot = slot<DialogEntity>()
                    val expectedBirthDate = LocalDate.of(1992, 3, 10)

                    coEvery { pdlService.getPersonInfo("01031992000") } returns
                        PdlPersonInfo(fullName = "Ola Nordmann", birthDate = "1992-03-10")
                    coEvery { dialogDAO.insertDialog(capture(insertedSlot)) } returns PersistedDialogEntity(
                        id = 1L,
                        title = "Sykefraværsoppfølging for Ola Nordmann (f. 10.03.1992)",
                        summary = "test",
                        fnr = doc.fnr,
                        orgNumber = doc.orgNumber,
                        birthDate = expectedBirthDate,
                        created = Instant.now(),
                        updated = Instant.now(),
                    )

                    // Act
                    val result = dialogService.insertDialog(doc)

                    // Assert
                    result.birthDate shouldBe expectedBirthDate
                    insertedSlot.captured.birthDate shouldBe expectedBirthDate
                    insertedSlot.captured.fnr shouldBe "01031992000"
                    insertedSlot.captured.orgNumber shouldBe "123456789"
                    insertedSlot.captured.title shouldBe "Sykefraværsoppfølging for Ola Nordmann (f. 10.03.1992)"
                    coVerify(exactly = 1) { pdlService.getPersonInfo("01031992000") }
                    coVerify(exactly = 1) { dialogDAO.insertDialog(any()) }
                }
            }

            context("when PDL returns birthDate for a d-nummer") {
                it("should generate title with PDL birthDate instead of fnr fallback") {
                    // Arrange — d-nummer: day + 40 → fnrToBirthDate returns null
                    val doc = document().copy(
                        fnr = "41011999000",
                        fullName = "Test Person",
                        orgNumber = "987654321",
                        birthDate = null,
                    )
                    val insertedSlot = slot<DialogEntity>()

                    coEvery { pdlService.getPersonInfo("41011999000") } returns
                        PdlPersonInfo(fullName = "Test Person", birthDate = "1999-01-01")
                    coEvery { dialogDAO.insertDialog(capture(insertedSlot)) } answers {
                        val captured = insertedSlot.captured
                        PersistedDialogEntity(
                            id = 2L,
                            title = captured.title,
                            summary = captured.summary,
                            fnr = captured.fnr,
                            orgNumber = captured.orgNumber,
                            birthDate = captured.birthDate,
                            created = Instant.now(),
                            updated = Instant.now(),
                        )
                    }

                    // Act
                    dialogService.insertDialog(doc)

                    // Assert — title uses formatted birthDate, NOT the raw fnr
                    insertedSlot.captured.birthDate shouldBe LocalDate.of(1999, 1, 1)
                    insertedSlot.captured.title shouldBe "Sykefraværsoppfølging for Test Person (f. 01.01.1999)"
                }
            }

            context("when PDL returns null") {
                it("should insert dialog without birthDate") {
                    // Arrange
                    val doc = document().copy(
                        fnr = "98765432109",
                        fullName = "Kari Nordmann",
                        orgNumber = "987654321",
                        birthDate = null,
                    )
                    val insertedSlot = slot<DialogEntity>()

                    coEvery { pdlService.getPersonInfo("98765432109") } returns
                        PdlPersonInfo(fullName = "Kari Nordmann", birthDate = null)
                    coEvery { dialogDAO.insertDialog(capture(insertedSlot)) } returns PersistedDialogEntity(
                        id = 3L,
                        title = "test",
                        summary = "test",
                        fnr = doc.fnr,
                        orgNumber = doc.orgNumber,
                        birthDate = null,
                        created = Instant.now(),
                        updated = Instant.now(),
                    )

                    // Act
                    val result = dialogService.insertDialog(doc)

                    // Assert
                    result.birthDate shouldBe null
                    insertedSlot.captured.birthDate shouldBe null
                    coVerify(exactly = 1) { pdlService.getPersonInfo("98765432109") }
                    coVerify(exactly = 1) { dialogDAO.insertDialog(any()) }
                }
            }

            context("when PDL returns an invalid date string") {
                it("should throw DateTimeParseException") {
                    // Arrange
                    val doc = document().copy(fnr = "33333333333")
                    coEvery { pdlService.getPersonInfo("33333333333") } returns
                        PdlPersonInfo(fullName = null, birthDate = "not-a-date")

                    // Act & Assert
                    shouldThrow<DateTimeParseException> {
                        dialogService.insertDialog(doc)
                    }
                    coVerify(exactly = 0) { dialogDAO.insertDialog(any()) }
                }
            }

            context("when dialogDAO.insertDialog throws exception") {
                it("should propagate the exception") {
                    // Arrange
                    val doc = document().copy(fnr = "44444444444")
                    coEvery { pdlService.getPersonInfo(any()) } returns
                        PdlPersonInfo(fullName = null, birthDate = "1990-01-01")
                    coEvery { dialogDAO.insertDialog(any()) } throws
                        RuntimeException("Database connection failed")

                    // Act & Assert
                    shouldThrow<RuntimeException> {
                        dialogService.insertDialog(doc)
                    }
                }
            }

            context("when document has null summary") {
                it("should insert dialog with generated summary") {
                    // Arrange
                    val doc = document().copy(
                        fnr = "55555555555",
                        fullName = "Person Uten Oppsummering",
                        orgNumber = "555555555",
                        summary = null,
                    )
                    val insertedSlot = slot<DialogEntity>()

                    coEvery { pdlService.getPersonInfo(any()) } returns
                        PdlPersonInfo(fullName = "Person Uten Oppsummering", birthDate = null)
                    coEvery { dialogDAO.insertDialog(capture(insertedSlot)) } answers {
                        val captured = insertedSlot.captured
                        PersistedDialogEntity(
                            id = 5L,
                            title = captured.title,
                            summary = captured.summary,
                            fnr = captured.fnr,
                            orgNumber = captured.orgNumber,
                            birthDate = captured.birthDate,
                            created = Instant.now(),
                            updated = Instant.now(),
                        )
                    }

                    // Act
                    dialogService.insertDialog(doc)

                    // Assert — toDialogEntity() always generates summary, regardless of document.summary
                    insertedSlot.captured.summary shouldNotBe null
                }
            }

            context("when document fnr and orgNumber are preserved") {
                it("should pass fnr and orgNumber through to the dialog entity") {
                    // Arrange
                    val doc = document().copy(
                        fnr = "22222222222",
                        orgNumber = "888888888",
                        fullName = "Preservert Person",
                    )
                    val insertedSlot = slot<DialogEntity>()

                    coEvery { pdlService.getPersonInfo(any()) } returns
                        PdlPersonInfo(fullName = "Preservert Person", birthDate = "2000-05-15")
                    coEvery { dialogDAO.insertDialog(capture(insertedSlot)) } returns PersistedDialogEntity(
                        id = 4L,
                        title = "test",
                        summary = "test",
                        fnr = doc.fnr,
                        orgNumber = doc.orgNumber,
                        birthDate = LocalDate.of(2000, 5, 15),
                        created = Instant.now(),
                        updated = Instant.now(),
                    )

                    // Act
                    dialogService.insertDialog(doc)

                    // Assert
                    insertedSlot.captured.fnr shouldBe "22222222222"
                    insertedSlot.captured.orgNumber shouldBe "888888888"
                    insertedSlot.captured.birthDate shouldBe LocalDate.of(2000, 5, 15)
                }
            }
        }

        describe("getAndUpdateDialogByFnrAndOrgNumber - edge cases") {
            context("when PDL returns an invalid date string") {
                it("should throw DateTimeParseException") {
                    // Arrange
                    val existingDialog = dialogEntity().copy(birthDate = null)
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { pdlService.getPersonInfo(any()) } returns
                        PdlPersonInfo(fullName = null, birthDate = "invalid-date")

                    // Act & Assert
                    shouldThrow<DateTimeParseException> {
                        dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                            existingDialog.fnr,
                            existingDialog.orgNumber,
                        )
                    }
                    coVerify(exactly = 0) { dialogDAO.updateDialogWithBirthDate(any(), any(), any()) }
                }
            }

            context("when updateDialogWithBirthDate throws exception") {
                it("should propagate the exception") {
                    // Arrange
                    val existingDialog = dialogEntity().copy(birthDate = null)
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { pdlService.getPersonInfo(any()) } returns
                        PdlPersonInfo(fullName = "Test Person", birthDate = "1990-01-15")
                    coEvery { dialogDAO.updateDialogWithBirthDate(any(), any(), any()) } throws
                        RuntimeException("Database error")

                    // Act & Assert
                    shouldThrow<RuntimeException> {
                        dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                            existingDialog.fnr,
                            existingDialog.orgNumber,
                        )
                    }
                }
            }

            context("when getByFnrAndOrgNumber throws exception") {
                it("should propagate the exception") {
                    // Arrange
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } throws
                        RuntimeException("Database unavailable")

                    // Act & Assert
                    shouldThrow<RuntimeException> {
                        dialogService.getAndUpdateDialogByFnrAndOrgNumber("12345678901", "123456789")
                    }
                    coVerify(exactly = 0) { pdlService.getPersonInfo(any()) }
                }
            }

            context("when enriching birthDate") {
                it("should regenerate title and preserve other fields in the returned copy") {
                    // Arrange — title has known pattern with fnr fallback (d-nummer case)
                    val existingDialog = dialogEntity().copy(
                        birthDate = null,
                        title = "Sykefraværsoppfølging for Test Person (41011999000)",
                    )
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { pdlService.getPersonInfo(any()) } returns
                        PdlPersonInfo(fullName = "Test Person", birthDate = "1990-01-15")
                    coEvery {
                        dialogDAO.updateDialogWithBirthDate(any(), any(), any())
                    } returns
                        existingDialog.copy(
                            birthDate = LocalDate.of(1990, 1, 15),
                            title = "Sykefraværsoppfølging for Test Person (f. 15.01.1990)",
                        )

                    // Act
                    val result = dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                        existingDialog.fnr,
                        existingDialog.orgNumber,
                    )

                    // Assert — title regenerated, other fields preserved
                    result shouldNotBe null
                    result?.id shouldBe existingDialog.id
                    result?.title shouldBe "Sykefraværsoppfølging for Test Person (f. 15.01.1990)"
                    result?.summary shouldBe existingDialog.summary
                    result?.fnr shouldBe existingDialog.fnr
                    result?.orgNumber shouldBe existingDialog.orgNumber
                    result?.dialogportenUUID shouldBe existingDialog.dialogportenUUID
                    result?.created shouldBe existingDialog.created
                    result?.updated shouldBe existingDialog.updated
                    result?.birthDate shouldBe LocalDate.of(1990, 1, 15)
                    coVerify(exactly = 0) { dialogDAO.getById(any()) }
                }
            }
        }
    })
