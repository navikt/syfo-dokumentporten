package no.nav.syfo.document.api.v1.dto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.application.exception.ApiErrorException
import varselInstruks

class VarselInstruksTest :
    DescribeSpec({
        describe("validate") {
            it("should trim fields before validation") {
                val trimmed = varselInstruks(
                    epostTittel = "  Tittel  ",
                    epostBody = "  Brødtekst  ",
                    smsTekst = "  SMS  ",
                    kilde = "  dokumentporten.dialogmote  ",
                ).trimmed()

                trimmed.notifikasjonInnhold.epostTittel shouldBe "Tittel"
                trimmed.notifikasjonInnhold.epostBody shouldBe "Brødtekst"
                trimmed.notifikasjonInnhold.smsTekst shouldBe "SMS"
                trimmed.kilde shouldBe "dokumentporten.dialogmote"
            }

            it("should throw when epostTittel is empty or blank") {
                listOf("", "   ").forEach { value ->
                    val exception = shouldThrow<ApiErrorException.BadRequestException> {
                        varselInstruks(epostTittel = value).validate()
                    }

                    exception.errorMessage shouldBe
                        "varselInstruks.notifikasjonInnhold.epostTittel må være satt"
                }
            }

            it("should throw when epostBody is empty or blank") {
                listOf("", "   ").forEach { value ->
                    val exception = shouldThrow<ApiErrorException.BadRequestException> {
                        varselInstruks(epostBody = value).validate()
                    }

                    exception.errorMessage shouldBe
                        "varselInstruks.notifikasjonInnhold.epostBody må være satt"
                }
            }

            it("should throw when smsTekst is empty or blank") {
                listOf("", "   ").forEach { value ->
                    val exception = shouldThrow<ApiErrorException.BadRequestException> {
                        varselInstruks(smsTekst = value).validate()
                    }

                    exception.errorMessage shouldBe
                        "varselInstruks.notifikasjonInnhold.smsTekst må være satt"
                }
            }

            it("should throw when kilde is empty or blank") {
                listOf("", "   ").forEach { value ->
                    val exception = shouldThrow<ApiErrorException.BadRequestException> {
                        varselInstruks(kilde = value).validate()
                    }

                    exception.errorMessage shouldBe
                        "varselInstruks.kilde må være satt"
                }
            }

            it("should throw when epostTittel exceeds max length") {
                val exception = shouldThrow<ApiErrorException.BadRequestException> {
                    varselInstruks(epostTittel = "a".repeat(256)).validate()
                }

                exception.errorMessage shouldBe
                    "varselInstruks.notifikasjonInnhold.epostTittel kan ikke være lengre enn 255 tegn"
            }

            it("should throw when epostBody exceeds max length") {
                val exception = shouldThrow<ApiErrorException.BadRequestException> {
                    varselInstruks(epostBody = "a".repeat(4001)).validate()
                }

                exception.errorMessage shouldBe
                    "varselInstruks.notifikasjonInnhold.epostBody kan ikke være lengre enn 4000 tegn"
            }

            it("should throw when smsTekst exceeds max length") {
                val exception = shouldThrow<ApiErrorException.BadRequestException> {
                    varselInstruks(smsTekst = "a".repeat(501)).validate()
                }

                exception.errorMessage shouldBe
                    "varselInstruks.notifikasjonInnhold.smsTekst kan ikke være lengre enn 500 tegn"
            }

            it("should throw when kilde exceeds max length") {
                val exception = shouldThrow<ApiErrorException.BadRequestException> {
                    varselInstruks(kilde = "a".repeat(256)).validate()
                }

                exception.errorMessage shouldBe
                    "varselInstruks.kilde kan ikke være lengre enn 255 tegn"
            }
        }
    })
