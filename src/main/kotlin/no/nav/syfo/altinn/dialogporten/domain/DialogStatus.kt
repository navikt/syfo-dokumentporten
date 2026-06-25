package no.nav.syfo.altinn.dialogporten.domain

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

enum class DialogStatus {
    New,
    InProgress,
    Draft,
    Sent,
    RequiresAttention,
    Completed,
    NotApplicable,

    @JsonEnumDefaultValue
    UNKNOWN
}
