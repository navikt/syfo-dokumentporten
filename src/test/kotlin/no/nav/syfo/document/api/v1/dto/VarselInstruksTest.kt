package no.nav.syfo.document.api.v1.dto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.application.exception.ApiErrorException
import varselInstruks

class VarselInstruksTest :
    DescribeSpec({
        describe("validate") {
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

            it("should throw max length error for ressursUrl before URL validation") {
                val exception = shouldThrow<ApiErrorException.BadRequestException> {
                    varselInstruks(ressursUrl = "a".repeat(2001)).validate()
                }

                exception.errorMessage shouldBe
                    "varselInstruks.ressursUrl kan ikke være lengre enn 2000 tegn"
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
