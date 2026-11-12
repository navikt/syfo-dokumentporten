package no.nav.syfo.altinn.dialogporten.client

import io.ktor.http.HttpStatusCode

class DialogportenClientException(message: String, val status: HttpStatusCode? = null,) : Exception(message)
