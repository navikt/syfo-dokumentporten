package no.nav.syfo.ereg.client

data class Organisasjon(
    val organisasjonsnummer: String,
    val inngaarIJuridiskEnheter: List<Organisasjon>? = null,
    val driverVirksomheter: List<Organisasjon>? = null,
)
