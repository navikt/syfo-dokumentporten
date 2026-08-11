package no.nav.syfo.altinn.dialogporten.domain

import java.util.UUID

data class Activity(val type: ActivityType, val transmissionId: UUID? = null, val performedBy: ActivityActor) {
    enum class ActivityType {
        DialogCreated,
        DialogClosed,
        Information,
        TransmissionOpened,
        PaymentMade,
        SignatureProvided,
        DialogOpened,
    }

    data class ActivityActor(val actorType: String, val actorName: String? = null, val actorId: String? = null)
}
