package no.nav.syfo.altinn.dialogporten

import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.altinn.common.AltinnTokenProvider
import no.nav.syfo.application.auth.AddTokenIssuerPlugin
import no.nav.syfo.texas.MaskinportenIdportenAndTokenXAuthPlugin
import no.nav.syfo.texas.client.TexasClient

fun Route.registerDialogportenTokenApi(texasClient: TexasClient, altinnTokenProvider: AltinnTokenProvider,) {
    route("/dialogporten/token") {
        install(AddTokenIssuerPlugin)
        install(MaskinportenIdportenAndTokenXAuthPlugin) {
            client = texasClient
        }
        get {
            val token = with(altinnTokenProvider) {
                token(AltinnTokenProvider.DIALOGPORTEN_TARGET_SCOPE)
                    .refresh()
                    .accessToken
            }
            call.respondText(token)
        }
    }
}
