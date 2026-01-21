package no.nav.syfo.application

import kotlin.String

data class ClientProperties(
    val altinnTilgangerBaseUrl: String,
    val eregBaseUrl: String,
    val electorPath: String,
    val altinn3BaseUrl: String,
    val pdpSubscriptionKey: String,
) {
    companion object {
        fun createForLocal() = ClientProperties(
            altinnTilgangerBaseUrl = "https://altinn-tilganger-api.dev.intern.nav.no",
            eregBaseUrl = "",
            electorPath = "syfo-dokumentporten",
            altinn3BaseUrl = "http://localhost:8080/dialogporten",
            pdpSubscriptionKey = "secret-key"
        )

        fun createFromEnvVars() = ClientProperties(
            eregBaseUrl = getEnvVar("EREG_BASE_URL"),
            altinnTilgangerBaseUrl = getEnvVar("ALTINN_TILGANGER_BASE_URL"),
            electorPath = getEnvVar("ELECTOR_PATH"),
            altinn3BaseUrl = getEnvVar("ALTINN_3_BASE_URL"),
            pdpSubscriptionKey = getEnvVar("PDP_SUBSCRIPTION_KEY")
        )
    }
}
