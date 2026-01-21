package no.nav.syfo.document.api.v1.dto

import document
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class DocumentTest :
    DescribeSpec({

        describe("toDialogEntity") {
            it("should create correct title and summary from fullName and fnr") {
                // Arrange
                val document = document().copy(
                    fullName = "Test Person",
                    fnr = "01011999000"
                )
                // Act
                val dialog = document.toDialogEntity()
                // Assert
                dialog.title shouldBe "Sykefraværsoppfølging for Test Person (f. 01.01.1999)"
                dialog.summary shouldBe """
                Her finner du alle dialogmøtebrev fra Nav og oppfølgingsplaner utarbeidet av nærmeste leder for Test Person.
                Innholdet er tilgjengelig i 4 måneder fra delingsdatoen. 
                """.trimIndent()
            }

            it("should create correct title and summary from fnr when fullName is null") {
                // Arrange
                val document = document().copy(
                    fullName = null,
                    fnr = "01011999000"
                )
                // Act
                val dialog = document.toDialogEntity()
                // Assert
                dialog.title shouldBe "Sykefraværsoppfølging for 01011999000 (f. 01.01.1999)"
                dialog.summary shouldBe """
                Her finner du alle dialogmøtebrev fra Nav og oppfølgingsplaner utarbeidet av nærmeste leder for 01011999000.
                Innholdet er tilgjengelig i 4 måneder fra delingsdatoen. 
                """.trimIndent()
            }

            it("should create correct title and summary from fullName and d-nummer") {
                // Arrange
                val document = document().copy(
                    fullName = "Test Person",
                    fnr = "41011999000" // d-nummer
                )
                // Act
                val dialog = document.toDialogEntity()
                // Assert
                dialog.title shouldBe "Sykefraværsoppfølging for Test Person (41011999000)"
                dialog.summary shouldBe """
                Her finner du alle dialogmøtebrev fra Nav og oppfølgingsplaner utarbeidet av nærmeste leder for Test Person.
                Innholdet er tilgjengelig i 4 måneder fra delingsdatoen. 
                """.trimIndent()
            }

            it("should create correct title and summary from d-nummer when fullName is null") {
                // Arrange
                val document = document().copy(
                    fullName = null,
                    fnr = "41011999000" // d-nummer
                )
                // Act
                val dialog = document.toDialogEntity()
                // Assert
                dialog.title shouldBe "Sykefraværsoppfølging for 41011999000"
                dialog.summary shouldBe """
                Her finner du alle dialogmøtebrev fra Nav og oppfølgingsplaner utarbeidet av nærmeste leder for 41011999000.
                Innholdet er tilgjengelig i 4 måneder fra delingsdatoen. 
                """.trimIndent()
            }

            it("should set correct orgNumber and fnr") {
                // Arrange
                val document = document()
                // Act
                val dialog = document.toDialogEntity()
                // Assert
                dialog.orgNumber shouldBe document.orgNumber
                dialog.fnr shouldBe document.fnr
            }
        }
    })
