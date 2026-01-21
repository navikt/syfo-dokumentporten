package no.nav.syfo.document.api.v1

import io.ktor.http.Parameters
import io.ktor.serialization.JsonConvertException
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.request.receive
import io.ktor.server.routing.RoutingCall
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.auth.JwtIssuer
import no.nav.syfo.application.auth.Principal
import no.nav.syfo.application.auth.SystemPrincipal
import no.nav.syfo.application.auth.TOKEN_ISSUER
import no.nav.syfo.application.exceptions.UnauthorizedException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

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
suspend inline fun <reified T : Any> RoutingCall.tryReceive() = runCatching { receive<T>() }.getOrElse {
    when {
        it is JsonConvertException -> throw BadRequestException("Invalid payload in request: ${it.message}", it)
        else -> throw it
    }
}
fun RoutingCall.getPrincipal(): Principal = when (attributes[TOKEN_ISSUER]) {
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
