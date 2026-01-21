package no.nav.syfo.altinn.dialogporten.domain

import java.time.Instant

data class Attachment(val displayName: List<ContentValueItem>, val urls: List<Url>, val expiresAt: Instant? = null,)

data class Url(val url: String, val mediaType: String, val consumerType: AttachmentUrlConsumerType,)

enum class AttachmentUrlConsumerType {
    Gui,
    Api,
}
