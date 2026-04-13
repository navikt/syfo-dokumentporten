package no.nav.syfo.pdl

import no.nav.syfo.pdl.client.IPdlClient
import no.nav.syfo.util.logger

data class PdlPersonInfo(val fullName: String?, val birthDate: String?,)

class PdlService(private val pdlClient: IPdlClient) {

    private val logger = logger()

    suspend fun getPersonInfo(fnr: String): PdlPersonInfo {
        val response = try {
            pdlClient.getPerson(fnr)
        } catch (e: Exception) {
            logger.error("Could not fetch person from PDL", e)
            return PdlPersonInfo(fullName = null, birthDate = null)
        }
        if (!response.errors.isNullOrEmpty()) {
            throw RuntimeException("PDL returned errors: ${response.errors}")
        }
        val person = response.data.person

        val navn = person?.navn?.firstOrNull()
        val fullName = listOfNotNull(navn?.fornavn, navn?.mellomnavn, navn?.etternavn)
            .joinToString(" ")
            .ifBlank { null }

        val birthDate = person?.foedselsdato?.firstOrNull()?.foedselsdato?.toString()
            ?.takeIf { it.isNotBlank() }

        logger.info("Fetched person with foedselsdato from PDL successfully")

        return PdlPersonInfo(fullName = fullName, birthDate = birthDate)
    }

    suspend fun getNameFor(fnr: String): String? = getPersonInfo(fnr).fullName

    suspend fun getBirthDateFor(fnr: String): String? = getPersonInfo(fnr).birthDate
}
