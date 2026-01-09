package no.nav.syfo.application.auth

import io.ktor.server.application.PipelineCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.path
import io.ktor.server.response.respondRedirect
import io.ktor.util.AttributeKey
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.texas.bearerToken
import no.nav.syfo.util.logger

val TOKEN_ISSUER = AttributeKey<JwtIssuer>("tokenIssuer")

val AddTokenIssuerPlugin = createRouteScopedPlugin(
    name = "AddTokenIssuerPlugin"
) {
    onCall { call ->
        call.setTokenIssuer()
    }
}

val AddTokenIssuerPluginWithRedirect = createRouteScopedPlugin(
    name = "AddTokenIssuerPluginWithRedirect"
) {
    onCall { call ->
        val bearerToken = call.bearerToken()
        if (bearerToken == null) {
            call.logger().warn("Missing bearer token, redirecting to /ouath2/login")
            call.respondRedirect("/oauth2/login?redirect=${call.request.path()}")
            return@onCall
        }
        call.setTokenIssuer()
    }
}

private fun PipelineCall.setTokenIssuer() {
    val issuer = try {
        this.jwtIssuer()
    } catch (e: Exception) {
        throw ApiErrorException.UnauthorizedException("Could not find token issuer: ${e.message}", e)
    }

    if (issuer == JwtIssuer.UNSUPPORTED) {
        throw ApiErrorException.UnauthorizedException("Unsupported token issuer")
    }

    this.attributes[TOKEN_ISSUER] = issuer
}
