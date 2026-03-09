package no.nav.syfo.document.service

import dialogEntity
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
                    coVerify(exactly = 0) { dialogDAO.updateDialogWithBirthDate(any(), any()) }
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
                    coVerify(exactly = 0) { dialogDAO.updateDialogWithBirthDate(any(), any()) }
                }
            }

            context("when dialog exists without birthDate and PDL returns birthDate") {
                it("should update dialog with birthDate and return enriched dialog") {
                    // Arrange
                    val existingDialog = dialogEntity().copy(birthDate = null)
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { pdlService.getBirthDateFor(existingDialog.fnr) } returns "1985-06-20"
                    coEvery { dialogDAO.updateDialogWithBirthDate(any(), any()) } returns Unit

                    // Act
                    val result = dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                        existingDialog.fnr,
                        existingDialog.orgNumber,
                    )

                    // Assert
                    result shouldNotBe null
                    result?.birthDate shouldBe LocalDate.of(1985, 6, 20)
                    result?.id shouldBe existingDialog.id
                    coVerify(exactly = 1) { pdlService.getBirthDateFor(existingDialog.fnr) }
                    coVerify(exactly = 1) {
                        dialogDAO.updateDialogWithBirthDate(existingDialog.id, LocalDate.of(1985, 6, 20))
                    }
                }
            }

            context("when dialog exists without birthDate and PDL returns null") {
                it("should return dialog without birthDate and not update") {
                    // Arrange
                    val existingDialog = dialogEntity().copy(birthDate = null)
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { pdlService.getBirthDateFor(any()) } returns null

                    // Act
                    val result = dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                        existingDialog.fnr,
                        existingDialog.orgNumber,
                    )

                    // Assert
                    result shouldBe existingDialog
                    result?.birthDate shouldBe null
                    coVerify(exactly = 1) { pdlService.getBirthDateFor(existingDialog.fnr) }
                    coVerify(exactly = 0) { dialogDAO.updateDialogWithBirthDate(any(), any()) }
                }
            }
        }

        describe("insertDialog") {
            context("when PDL returns a birthDate") {
                it("should insert dialog with birthDate from PDL") {
                    // Arrange
                    val dialogEntity = DialogEntity(
                        title = "Innkalling til dialogmøte",
                        summary = "Kort oppsummering",
                        fnr = "12345678901",
                        orgNumber = "123456789",
                    )
                    val insertedSlot = slot<DialogEntity>()
                    val expectedBirthDate = LocalDate.of(1992, 3, 10)

                    coEvery { pdlService.getBirthDateFor("12345678901") } returns "1992-03-10"
                    coEvery { dialogDAO.insertDialog(capture(insertedSlot)) } returns PersistedDialogEntity(
                        id = 1L,
                        title = dialogEntity.title,
                        summary = dialogEntity.summary,
                        fnr = dialogEntity.fnr,
                        orgNumber = dialogEntity.orgNumber,
                        birthDate = expectedBirthDate,
                        created = Instant.now(),
                        updated = Instant.now(),
                    )

                    // Act
                    val result = dialogService.insertDialog(dialogEntity)

                    // Assert
                    result.birthDate shouldBe expectedBirthDate
                    insertedSlot.captured.birthDate shouldBe expectedBirthDate
                    insertedSlot.captured.fnr shouldBe "12345678901"
                    insertedSlot.captured.orgNumber shouldBe "123456789"
                    coVerify(exactly = 1) { pdlService.getBirthDateFor("12345678901") }
                    coVerify(exactly = 1) { dialogDAO.insertDialog(any()) }
                }
            }

            context("when PDL returns null") {
                it("should insert dialog without birthDate") {
                    // Arrange
                    val dialogEntity = DialogEntity(
                        title = "Oppfølgingsplan",
                        summary = "Oppsummering",
                        fnr = "98765432109",
                        orgNumber = "987654321",
                    )
                    val insertedSlot = slot<DialogEntity>()

                    coEvery { pdlService.getBirthDateFor("98765432109") } returns null
                    coEvery { dialogDAO.insertDialog(capture(insertedSlot)) } returns PersistedDialogEntity(
                        id = 2L,
                        title = dialogEntity.title,
                        summary = dialogEntity.summary,
                        fnr = dialogEntity.fnr,
                        orgNumber = dialogEntity.orgNumber,
                        birthDate = null,
                        created = Instant.now(),
                        updated = Instant.now(),
                    )

                    // Act
                    val result = dialogService.insertDialog(dialogEntity)

                    // Assert
                    result.birthDate shouldBe null
                    insertedSlot.captured.birthDate shouldBe null
                    coVerify(exactly = 1) { pdlService.getBirthDateFor("98765432109") }
                    coVerify(exactly = 1) { dialogDAO.insertDialog(any()) }
                }
            }

            context("when dialog already has birthDate set") {
                it("should still call PDL and use PDL value") {
                    // Arrange
                    val dialogEntity = DialogEntity(
                        title = "Dialogmøte",
                        summary = "Oppsummering",
                        fnr = "11111111111",
                        orgNumber = "999999999",
                        birthDate = LocalDate.of(2000, 1, 1),
                    )
                    val insertedSlot = slot<DialogEntity>()

                    coEvery { pdlService.getBirthDateFor("11111111111") } returns "1995-12-25"
                    coEvery { dialogDAO.insertDialog(capture(insertedSlot)) } returns PersistedDialogEntity(
                        id = 3L,
                        title = dialogEntity.title,
                        summary = dialogEntity.summary,
                        fnr = dialogEntity.fnr,
                        orgNumber = dialogEntity.orgNumber,
                        birthDate = LocalDate.of(1995, 12, 25),
                        created = Instant.now(),
                        updated = Instant.now(),
                    )

                    // Act
                    val result = dialogService.insertDialog(dialogEntity)

                    // Assert
                    result.birthDate shouldBe LocalDate.of(1995, 12, 25)
                    insertedSlot.captured.birthDate shouldBe LocalDate.of(1995, 12, 25)
                    coVerify(exactly = 1) { pdlService.getBirthDateFor("11111111111") }
                }
            }

            context("when dialog entity preserves all fields during insert") {
                it("should pass title, summary, fnr, orgNumber, and dialogportenUUID through") {
                    // Arrange
                    val dialogEntity = DialogEntity(
                        title = "My Title",
                        summary = "My Summary",
                        fnr = "22222222222",
                        orgNumber = "888888888",
                    )
                    val insertedSlot = slot<DialogEntity>()

                    coEvery { pdlService.getBirthDateFor(any()) } returns "2000-05-15"
                    coEvery { dialogDAO.insertDialog(capture(insertedSlot)) } returns PersistedDialogEntity(
                        id = 4L,
                        title = dialogEntity.title,
                        summary = dialogEntity.summary,
                        fnr = dialogEntity.fnr,
                        orgNumber = dialogEntity.orgNumber,
                        birthDate = LocalDate.of(2000, 5, 15),
                        created = Instant.now(),
                        updated = Instant.now(),
                    )

                    // Act
                    dialogService.insertDialog(dialogEntity)

                    // Assert
                    insertedSlot.captured.title shouldBe "My Title"
                    insertedSlot.captured.summary shouldBe "My Summary"
                    insertedSlot.captured.fnr shouldBe "22222222222"
                    insertedSlot.captured.orgNumber shouldBe "888888888"
                    insertedSlot.captured.birthDate shouldBe LocalDate.of(2000, 5, 15)
                }
            }

            context("when PDL returns an invalid date string") {
                it("should throw DateTimeParseException") {
                    // Arrange
                    val dialogEntity = DialogEntity(
                        title = "Title",
                        summary = "Summary",
                        fnr = "33333333333",
                        orgNumber = "777777777",
                    )
                    coEvery { pdlService.getBirthDateFor("33333333333") } returns "not-a-date"

                    // Act & Assert
                    shouldThrow<DateTimeParseException> {
                        dialogService.insertDialog(dialogEntity)
                    }
                    coVerify(exactly = 0) { dialogDAO.insertDialog(any()) }
                }
            }

            context("when dialogDAO.insertDialog throws exception") {
                it("should propagate the exception") {
                    // Arrange
                    val dialogEntity = DialogEntity(
                        title = "Title",
                        summary = "Summary",
                        fnr = "44444444444",
                        orgNumber = "666666666",
                    )
                    coEvery { pdlService.getBirthDateFor(any()) } returns "1990-01-01"
                    coEvery { dialogDAO.insertDialog(any()) } throws
                        RuntimeException("Database connection failed")

                    // Act & Assert
                    shouldThrow<RuntimeException> {
                        dialogService.insertDialog(dialogEntity)
                    }
                }
            }

            context("when dialog has null summary") {
                it("should insert dialog preserving null summary") {
                    // Arrange
                    val dialogEntity = DialogEntity(
                        title = "Title without summary",
                        summary = null,
                        fnr = "55555555555",
                        orgNumber = "555555555",
                    )
                    val insertedSlot = slot<DialogEntity>()

                    coEvery { pdlService.getBirthDateFor(any()) } returns null
                    coEvery { dialogDAO.insertDialog(capture(insertedSlot)) } returns PersistedDialogEntity(
                        id = 5L,
                        title = dialogEntity.title,
                        summary = null,
                        fnr = dialogEntity.fnr,
                        orgNumber = dialogEntity.orgNumber,
                        birthDate = null,
                        created = Instant.now(),
                        updated = Instant.now(),
                    )

                    // Act
                    val result = dialogService.insertDialog(dialogEntity)

                    // Assert
                    result.summary shouldBe null
                    insertedSlot.captured.summary shouldBe null
                }
            }
        }

        describe("getAndUpdateDialogByFnrAndOrgNumber - edge cases") {
            context("when PDL returns an invalid date string") {
                it("should throw DateTimeParseException") {
                    // Arrange
                    val existingDialog = dialogEntity().copy(birthDate = null)
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { pdlService.getBirthDateFor(any()) } returns "invalid-date"

                    // Act & Assert
                    shouldThrow<DateTimeParseException> {
                        dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                            existingDialog.fnr,
                            existingDialog.orgNumber,
                        )
                    }
                    coVerify(exactly = 0) { dialogDAO.updateDialogWithBirthDate(any(), any()) }
                }
            }

            context("when updateDialogWithBirthDate throws exception") {
                it("should propagate the exception") {
                    // Arrange
                    val existingDialog = dialogEntity().copy(birthDate = null)
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { pdlService.getBirthDateFor(any()) } returns "1990-01-15"
                    coEvery { dialogDAO.updateDialogWithBirthDate(any(), any()) } throws
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
                    coVerify(exactly = 0) { pdlService.getBirthDateFor(any()) }
                }
            }

            context("when enriching birthDate") {
                it("should preserve all original dialog fields in the returned copy") {
                    // Arrange
                    val existingDialog = dialogEntity().copy(birthDate = null)
                    coEvery { dialogDAO.getByFnrAndOrgNumber(any(), any()) } returns existingDialog
                    coEvery { pdlService.getBirthDateFor(any()) } returns "1990-01-15"
                    coEvery { dialogDAO.updateDialogWithBirthDate(any(), any()) } returns Unit

                    // Act
                    val result = dialogService.getAndUpdateDialogByFnrAndOrgNumber(
                        existingDialog.fnr,
                        existingDialog.orgNumber,
                    )

                    // Assert — all fields preserved except birthDate
                    result shouldNotBe null
                    result?.id shouldBe existingDialog.id
                    result?.title shouldBe existingDialog.title
                    result?.summary shouldBe existingDialog.summary
                    result?.fnr shouldBe existingDialog.fnr
                    result?.orgNumber shouldBe existingDialog.orgNumber
                    result?.dialogportenUUID shouldBe existingDialog.dialogportenUUID
                    result?.created shouldBe existingDialog.created
                    result?.updated shouldBe existingDialog.updated
                    result?.birthDate shouldBe LocalDate.of(1990, 1, 15)
                }
            }
        }
    })

