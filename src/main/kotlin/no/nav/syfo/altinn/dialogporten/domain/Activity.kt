package no.nav.syfo.altinn.dialogporten.domain

import java.util.UUID

data class Activity(
    val id: UUID,
    val type: ActivityType,
    val transmissionId: UUID? = null,
    val performedBy: ActivityActor,
) {
    enum class ActivityType {
        TransmissionOpened,
    }

    enum class ActorType {
        ServiceOwner,
    }

    data class ActivityActor(val actorType: ActorType)
}
