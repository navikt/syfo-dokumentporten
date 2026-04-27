package no.nav.syfo.document.api.v1.dto

import document
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import testDocumentConfig
import java.time.LocalDate

class DocumentTest :
    DescribeSpec({
        val documentConfig = testDocumentConfig()
        fun dialogSummary(name: String) = documentConfig.dialogSummaryTemplate.replace("{name}", name)

        describe("toDialogEntity") {
            it("should create correct title and summary from fullName and fnr") {
                // Arrange
                val document = document().copy(
                    fullName = "Test Person",
                    fnr = "01011999000"
                )
                // Act
                val dialog = document.toDialogEntity(dialogSummary("Test Person"))
                // Assert
                dialog.title shouldBe "Sykefraværsoppfølging for Test Person (f. 01.01.1999)"
                dialog.summary shouldBe dialogSummary("Test Person")
            }

            it("should create correct title and summary from fnr when fullName is null") {
                // Arrange
                val document = document().copy(
                    fullName = null,
                    fnr = "01011999000"
                )
                // Act
                val dialog = document.toDialogEntity(dialogSummary(document.fnr))
                // Assert
                dialog.title shouldBe "Sykefraværsoppfølging for 01011999000 (f. 01.01.1999)"
                dialog.summary shouldBe dialogSummary("01011999000")
            }

            it("should create correct title and summary from fullName and d-nummer") {
                // Arrange
                val document = document().copy(
                    fullName = "Test Person",
                    fnr = "41011999000", // d-nummer
                    birthDate = LocalDate.parse("2000-12-31"),
                )
                // Act
                val dialog = document.toDialogEntity(dialogSummary("Test Person"))
                // Assert
                dialog.title shouldBe "Sykefraværsoppfølging for Test Person (f. 31.12.2000)"
                dialog.summary shouldBe dialogSummary("Test Person")
            }

            it("should create correct title and summary from d-nummer when fullName is null") {
                // Arrange
                val document = document().copy(
                    fullName = null,
                    fnr = "41011999000", // d-nummer
                    birthDate = LocalDate.parse("2000-12-31"),
                )
                // Act
                val dialog = document.toDialogEntity(dialogSummary(document.fnr))
                // Assert
                dialog.title shouldBe "Sykefraværsoppfølging for 41011999000 (f. 31.12.2000)"
                dialog.summary shouldBe dialogSummary("41011999000")
            }
            it("should set correct orgNumber and fnr") {
                // Arrange
                val document = document()
                // Act
                val dialog = document.toDialogEntity(dialogSummary(document.fullName ?: document.fnr))
                // Assert
                dialog.orgNumber shouldBe document.orgNumber
                dialog.fnr shouldBe document.fnr
            }
        }
    })
