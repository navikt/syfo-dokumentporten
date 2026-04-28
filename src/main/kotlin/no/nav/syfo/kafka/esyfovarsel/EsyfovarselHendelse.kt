package no.nav.syfo.kafka.esyfovarsel

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.syfo.document.db.VarselInstruksPublishView

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes(
    JsonSubTypes.Type(
        value = ArbeidsgiverNotifikasjonTilAltinnRessursHendelse::class,
        name = "ArbeidsgiverNotifikasjonTilAltinnRessursHendelse"
    )
)
sealed interface EsyfovarselHendelse {
    val type: HendelseType
    val ferdigstill: Boolean?
    val data: Any?
}

data class ArbeidsgiverNotifikasjonTilAltinnRessursHendelse(
    override val type: HendelseType,
    override val ferdigstill: Boolean?,
    override val data: VarselData?,
    val arbeidstakerFnr: String? = null,
    val eksternReferanseId: String,
    val kilde: String,
    val orgnummer: String,
    val ressursId: String,
    val ressursUrl: String,
) : EsyfovarselHendelse

data class VarselData(val notifikasjonInnhold: VarselDataNotifikasjonInnhold? = null)

data class VarselDataNotifikasjonInnhold(val epostTittel: String, val epostBody: String, val smsTekst: String)

enum class HendelseType {
    AG_VARSEL_ALTINN_RESSURS,
}

fun VarselInstruksPublishView.toEsyfovarselHendelse(): ArbeidsgiverNotifikasjonTilAltinnRessursHendelse =
    ArbeidsgiverNotifikasjonTilAltinnRessursHendelse(
        type = HendelseType.AG_VARSEL_ALTINN_RESSURS,
        ferdigstill = false,
        data = VarselData(
            notifikasjonInnhold = VarselDataNotifikasjonInnhold(
                epostTittel = epostTittel,
                epostBody = epostBody,
                smsTekst = smsTekst,
            )
        ),
        arbeidstakerFnr = fnr,
        eksternReferanseId = documentId.toString(),
        kilde = kilde,
        orgnummer = orgNumber,
        ressursId = ressursId,
        ressursUrl = ressursUrl,
    )
