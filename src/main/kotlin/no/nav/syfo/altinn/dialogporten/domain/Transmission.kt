package no.nav.syfo.altinn.dialogporten.domain

import java.util.UUID

data class Transmission(
    val id: UUID? = null,
    val type: TransmissionType,
    val extendedType: String? = null,
    val externalReference: String? = null,
    val sender: Sender,
    val content: Content,
    val attachments: List<Attachment> = emptyList(),
) {
    data class Sender(val actorType: String,)

    enum class TransmissionType {
        // For general information, not related to any submissions
        Information,

        // Feedback/receipt accepting a previous submission
        Acceptance,

        // Feedback/error message rejecting a previous submission
        Rejection,

        // Question/request for more information
        Request,
    }
}
