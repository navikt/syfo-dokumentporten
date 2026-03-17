package no.nav.syfo.ereg.client

import no.nav.syfo.util.JsonFixtureLoader
import java.util.concurrent.atomic.*

class FakeEregClient(fixtureLoader: JsonFixtureLoader = defaultFixtureLoader) : IEaregClient {
    /**
     * Mutable map of orgnummer -> Organisasjon for test manipulation.
     * Pre-populated from the fixture file.
     */
    val organisasjoner: MutableMap<String, Organisasjon> = loadOrganisasjoner(fixtureLoader).toMutableMap()
    private val failureRef = AtomicReference<Throwable?>(null)

    fun setFailure(failure: Throwable) {
        failureRef.set(failure)
    }

    fun clearFailure() = failureRef.set(null)

    override suspend fun getOrganisasjon(orgnummer: String): Organisasjon? {
        failureRef.get()?.let { throw it }
        return when (orgnummer) {
            "314602374", "987926279" -> defaultFixtureLoader.loadOrNull<Organisasjon>("$orgnummer.json")
            else -> organisasjoner[orgnummer]
        }
    }

    companion object {
        val defaultOrganisasjoner = mapOf(
            "310667633" to Organisasjon(
                organisasjonsnummer = "310667633",
                driverVirksomheter = listOf(
                    Organisasjon(organisasjonsnummer = "215649202"),
                    Organisasjon(organisasjonsnummer = "315649196"),
                    Organisasjon(organisasjonsnummer = "315649218"),
                )
            ),
            "215649202" to Organisasjon(
                organisasjonsnummer = "215649202",
                inngaarIJuridiskEnheter = listOf(
                    Organisasjon(organisasjonsnummer = "310667633"),
                ),
            ),
            "315649196" to Organisasjon(
                organisasjonsnummer = "315649196",
                inngaarIJuridiskEnheter = listOf(
                    Organisasjon(organisasjonsnummer = "310667633"),
                ),
            ),
            "315649218" to Organisasjon(
                organisasjonsnummer = "315649218",
                inngaarIJuridiskEnheter = listOf(
                    Organisasjon(organisasjonsnummer = "310667633"),
                )
            )
        )
        private const val FIXTURE_FILE = "organisasjoner.json"
        private val defaultFixtureLoader = JsonFixtureLoader("classpath:fake-clients/ereg")
        private fun loadOrganisasjoner(fixtureLoader: JsonFixtureLoader): Map<String, Organisasjon> =
            fixtureLoader.loadOrNull<List<Organisasjon>>(FIXTURE_FILE)
                ?.associateBy { it.organisasjonsnummer } ?: emptyMap()
    }
}
