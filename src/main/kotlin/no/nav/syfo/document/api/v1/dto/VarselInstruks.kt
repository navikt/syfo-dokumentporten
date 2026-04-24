package no.nav.syfo.document.api.v1.dto

import no.nav.syfo.application.exception.ApiErrorException
import java.net.URI

data class VarselInstruks(
    val type: HendelseType,
    val notifikasjonInnhold: NotifikasjonInnhold,
    val ressursUrl: String,
    val kilde: String,
)

data class NotifikasjonInnhold(val epostTittel: String, val epostBody: String, val smsTekst: String,)

enum class HendelseType {
    AG_VARSEL_ALTINN_RESSURS
}

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

    if (ressursUrl.isBlank()) {
        throw ApiErrorException.BadRequestException("varselInstruks.ressursUrl må være satt")
    }

    if (kilde.isBlank()) {
        throw ApiErrorException.BadRequestException("varselInstruks.kilde må være satt")
    }

    val uri = runCatching { URI(ressursUrl) }.getOrElse {
        throw ApiErrorException.BadRequestException("varselInstruks.ressursUrl må være en gyldig URL")
    }

    if (uri.scheme?.lowercase() !in setOf("http", "https")) {
        throw ApiErrorException.BadRequestException("varselInstruks.ressursUrl må være en gyldig URL")
    }

    if (uri.host.isNullOrBlank()) {
        throw ApiErrorException.BadRequestException("varselInstruks.ressursUrl må ha et host")
    }
}
