package no.nav.syfo.document.api.v1.dto

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

data class VarselInstruks(val varselType: ArbeidsgiverVarselType)

enum class ArbeidsgiverVarselType {
    INNKALT,
    AVLYST,
    NYTT_TID_STED,
    REFERAT,

    @JsonEnumDefaultValue
    UNKNOWN,
}
