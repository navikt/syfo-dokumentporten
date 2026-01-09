package no.nav.syfo.texas

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.authentication
import io.ktor.server.request.path
import io.ktor.server.response.respondNullable
import io.ktor.server.response.respondRedirect
import io.ktor.util.AttributeKey
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.auth.JwtIssuer
import no.nav.syfo.application.auth.SystemPrincipal
import no.nav.syfo.application.auth.TOKEN_ISSUER
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.texas.client.OrganizationId
import no.nav.syfo.texas.client.getSystemUserId
import no.nav.syfo.texas.client.getSystemUserOrganization
import no.nav.syfo.util.logger

val TOKEN_CONSUMER_KEY = AttributeKey<OrganizationId>("tokenConsumer")
private val VALID_ISSUERS = listOf(JwtIssuer.MASKINPORTEN, JwtIssuer.TOKEN_X, JwtIssuer.IDPORTEN)
val MASKINPORTEN_ARKIVPORTEN_SCOPE = "nav:syfo/arkivporten"
const val MASKINPORTEN_SYFO_DOKUMENTPORTEN_SCOPE = "nav:syfo/dokumentporten"
private val logger = logger("no.nav.syfo.texas.MaskinportenIdportenAndTokenXAuthPlugin")

val MaskinportenIdportenAndTokenXAuthPlugin = createRouteScopedPlugin(
    name = "MaskinportenIdportenAndTokenXAuthPlugin",
    createConfiguration = ::TexasAuthPluginConfiguration,
) {

    pluginConfig.apply {
        onCall { call ->
            val issuer = try {
                call.attributes.getOrNull(TOKEN_ISSUER)
                    ?.takeIf { it in VALID_ISSUERS }
                    ?: error("Missing or invalid token issuer")
            } catch (e: Exception) {
                throw ApiErrorException.UnauthorizedException("Failed to find issuer in token: ${e.message}", e)
            }

            val bearerToken =
                call.bearerToken() ?: throw ApiErrorException.UnauthorizedException("No bearer token found in request")

            val introspectionResponse = try {
                client?.introspectToken(issuer.value!!, bearerToken)
                    ?: error("TexasHttpClient is not configured")
            } catch (e: Exception) {
                throw ApiErrorException.UnauthorizedException("Failed to introspect token: ${e.message}", e)
            }

            if (!introspectionResponse.active) {
                throw ApiErrorException.UnauthorizedException(
                    "Token is not active: ${introspectionResponse.error ?: "No error message"}"
                )
            }

            when (issuer) {
                JwtIssuer.MASKINPORTEN -> {
                    if (introspectionResponse.consumer == null) {
                        throw ApiErrorException.UnauthorizedException("No consumer in token claims")
                    }
                    if (!(introspectionResponse.scope == MASKINPORTEN_ARKIVPORTEN_SCOPE || introspectionResponse.scope == MASKINPORTEN_SYFO_DOKUMENTPORTEN_SCOPE)) {
                        throw ApiErrorException.UnauthorizedException("Invalid scope from maskinporten")
                    }
                    val systemUserOrganizationId = introspectionResponse.getSystemUserOrganization()
                        ?: throw ApiErrorException.UnauthorizedException("No system user organization number in token claims")
                    val systemUserId = introspectionResponse.getSystemUserId()
                        ?: throw ApiErrorException.UnauthorizedException("No system user id in token claims")

                    call.authentication.principal(
                        SystemPrincipal(
                            ident = systemUserOrganizationId,
                            token = bearerToken,
                            systemOwner = introspectionResponse.consumer.ID,
                            systemUserId = systemUserId,
                        )
                    )
                    call.attributes.put(TOKEN_CONSUMER_KEY, introspectionResponse.consumer)
                }

                JwtIssuer.IDPORTEN -> {
                    if (!introspectionResponse.acr.equals("idporten-loa-high", ignoreCase = true)) {
                        call.application.environment.log.warn("User does not have Level4 access: ${introspectionResponse.acr}. Redirecting to /oauth2/login")
                        call.respondRedirect("/oauth2/login?redirect=${call.request.path()}")
                        return@onCall
                    }

                    if (introspectionResponse.pid == null) {
                        call.application.environment.log.warn("No pid in token claims")
                        call.respondNullable(HttpStatusCode.Forbidden)
                        return@onCall
                    }
                    call.authentication.principal(BrukerPrincipal(introspectionResponse.pid, bearerToken))
                }

                JwtIssuer.TOKEN_X -> {
                    if (!introspectionResponse.acr.equals("Level4", ignoreCase = true)) {
                        call.application.environment.log.warn("User does not have Level4 access: ${introspectionResponse.acr}")
                        call.respondNullable(HttpStatusCode.Forbidden)
                        return@onCall
                    }

                    if (introspectionResponse.pid == null) {
                        call.application.environment.log.warn("No pid in token claims")
                        call.respondNullable(HttpStatusCode.Unauthorized)
                        return@onCall
                    }
                    call.authentication.principal(BrukerPrincipal(introspectionResponse.pid, bearerToken))
                }

                else -> {
                    throw ApiErrorException.UnauthorizedException("Unsupported token issuer")
                }
            }
        }
    }
    logger.info("TexasMaskinportenAuthPlugin installed")
}
