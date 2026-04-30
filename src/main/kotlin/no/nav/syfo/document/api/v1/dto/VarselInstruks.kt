package no.nav.syfo.document.api.v1.dto

import no.nav.syfo.application.exception.ApiErrorException

data class VarselInstruks(val type: HendelseType, val notifikasjonInnhold: NotifikasjonInnhold, val kilde: String,)

data class NotifikasjonInnhold(val epostTittel: String, val epostBody: String, val smsTekst: String,)

enum class HendelseType {
    AG_VARSEL_ALTINN_RESSURS
}

private const val EPOST_TITTEL_MAX_LENGTH = 255
private const val EPOST_BODY_MAX_LENGTH = 4000
private const val SMS_TEKST_MAX_LENGTH = 500
private const val KILDE_MAX_LENGTH = 255

fun VarselInstruks.trimmed(): VarselInstruks = copy(
    notifikasjonInnhold = notifikasjonInnhold.trimmed(),
    kilde = kilde.trim(),
)

fun NotifikasjonInnhold.trimmed(): NotifikasjonInnhold = copy(
    epostTittel = epostTittel.trim(),
    epostBody = epostBody.trim(),
    smsTekst = smsTekst.trim(),
)

fun VarselInstruks.validate() {
    if (notifikasjonInnhold.epostTittel.isBlank()) {
        throw ApiErrorException.BadRequestException("varselInstruks.notifikasjonInnhold.epostTittel må være satt")
    }

    if (notifikasjonInnhold.epostBody.isBlank()) {
        throw ApiErrorException.BadRequestException("varselInstruks.notifikasjonInnhold.epostBody må være satt")
    }

    if (notifikasjonInnhold.smsTekst.isBlank()) {
        throw ApiErrorException.BadRequestException("varselInstruks.notifikasjonInnhold.smsTekst må være satt")
    }

    if (kilde.isBlank()) {
        throw ApiErrorException.BadRequestException("varselInstruks.kilde må være satt")
    }

    validateMaxLength(
        fieldName = "varselInstruks.notifikasjonInnhold.epostTittel",
        value = notifikasjonInnhold.epostTittel,
        maxLength = EPOST_TITTEL_MAX_LENGTH,
    )
    validateMaxLength(
        fieldName = "varselInstruks.notifikasjonInnhold.epostBody",
        value = notifikasjonInnhold.epostBody,
        maxLength = EPOST_BODY_MAX_LENGTH,
    )
    validateMaxLength(
        fieldName = "varselInstruks.notifikasjonInnhold.smsTekst",
        value = notifikasjonInnhold.smsTekst,
        maxLength = SMS_TEKST_MAX_LENGTH,
    )
    validateMaxLength(
        fieldName = "varselInstruks.kilde",
        value = kilde,
        maxLength = KILDE_MAX_LENGTH,
    )
}

private fun validateMaxLength(fieldName: String, value: String, maxLength: Int) {
    if (value.length > maxLength) {
        throw ApiErrorException.BadRequestException("$fieldName kan ikke være lengre enn $maxLength tegn")
    }
}
