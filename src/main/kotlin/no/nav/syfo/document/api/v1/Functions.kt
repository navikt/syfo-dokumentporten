package no.nav.syfo.document.api.v1

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.auth.JwtIssuer
import no.nav.syfo.application.auth.Principal
import no.nav.syfo.application.auth.SystemPrincipal
import no.nav.syfo.application.auth.TOKEN_ISSUER
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.exceptions.UnauthorizedException
import no.nav.syfo.document.api.v1.dto.DocumentType
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.*

fun Parameters.extractAndValidateUUIDParameter(name: String): UUID {
    val parameter = get(name)
    if (parameter == null) {
        throw BadRequestException("Missing parameter: $name")
    }

    return try {
        UUID.fromString(parameter)
    } catch (e: IllegalArgumentException) {
        throw ParameterConversionException("uuid", "UUID", e)
    }
}

fun Parameters.extractDocumentTypeParameter(name: String): DocumentType {
    val parameter = get(name) ?: throw BadRequestException("Missing parameter: $name")

    return try {
        DocumentType.valueOf(parameter)
    } catch (e: IllegalArgumentException) {
        throw ParameterConversionException("documentType", "DocumentType", e)
    }
}

fun RoutingCall.getRequiredQueryParameter(name: String): String {
    return this.queryParameters[name] ?: throw ApiErrorException.BadRequestException("Missing $name parameter")
}

fun RoutingCall.getCreatedAfter(): Instant {
    val createdAfter = getRequiredQueryParameter("createdAfter")
    try {
        return Instant.parse(createdAfter)
    } catch (e: DateTimeParseException) {
        throw ApiErrorException.BadRequestException(
            "Invalid date format for createdAfter parameter. Expected ISO-8601 format."
        )
    }
}

fun RoutingCall.getPageSize(): Int? =
    this.queryParameters["pageSize"]
        ?.toIntOrNull()


fun RoutingCall.getPage(): Int? =
    this.queryParameters["page"]
        ?.toIntOrNull()


suspend inline fun <reified T : Any> RoutingCall.tryReceive() = runCatching { receive<T>() }.getOrElse {
    when {
        it is JsonConvertException -> throw BadRequestException("Invalid payload in request: ${it.message}", it)
        else -> throw it
    }
}

fun RoutingCall.getPrincipal(): Principal =
    when (attributes[TOKEN_ISSUER]) {
        JwtIssuer.MASKINPORTEN -> {
            authentication.principal<SystemPrincipal>() ?: throw UnauthorizedException()
        }

        JwtIssuer.TOKEN_X -> {
            authentication.principal<BrukerPrincipal>() ?: throw UnauthorizedException()
        }

        JwtIssuer.IDPORTEN -> {
            authentication.principal<BrukerPrincipal>() ?: throw UnauthorizedException()
        }

        else -> throw UnauthorizedException()
    }

fun fnrToBirthDate(fnr: String): LocalDate? {
    try {
        val day = fnr.take(2)
        val month = fnr.substring(2, 4)
        val year = fnr.substring(4, 8)
        return LocalDate.parse("$year-$month-$day")
    } catch (e: DateTimeParseException) {
        return null
    }

}
