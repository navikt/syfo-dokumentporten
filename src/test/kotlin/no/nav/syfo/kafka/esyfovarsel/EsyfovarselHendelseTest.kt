package no.nav.syfo.kafka.esyfovarsel

import document
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.document.db.VarselInstruksPublishView
import varselInstruks

class EsyfovarselHendelseTest :
    DescribeSpec({
        describe("toEsyfovarselHendelse") {
            it("should map publish view to esyfovarsel contract") {
                val document = document(varselInstruks = varselInstruks())
                val publishView = VarselInstruksPublishView(
                    id = 1L,
                    documentId = document.documentId,
                    fnr = document.fnr,
                    orgNumber = document.orgNumber,
                    ressursId = "nav_syfo_dialog",
                    dokumentUrl = "https://test.nav.no/api/v1/gui/documents/test-link",
                    kilde = document.varselInstruks!!.kilde,
                    epostTittel = document.varselInstruks.notifikasjonInnhold.epostTittel,
                    epostBody = document.varselInstruks.notifikasjonInnhold.epostBody,
                    smsTekst = document.varselInstruks.notifikasjonInnhold.smsTekst,
                )

                val hendelse = publishView.toEsyfovarselHendelse()

                hendelse.type shouldBe HendelseType.AG_VARSEL_ALTINN_RESSURS
                hendelse.ferdigstill shouldBe false
                hendelse.arbeidstakerFnr shouldBe document.fnr
                hendelse.eksternReferanseId shouldBe document.documentId.toString()
                hendelse.kilde shouldBe document.varselInstruks.kilde
                hendelse.orgnummer shouldBe document.orgNumber
                hendelse.ressursId shouldBe "nav_syfo_dialog"
                hendelse.ressursUrl shouldBe "https://test.nav.no/api/v1/gui/documents/test-link"
                hendelse.data?.notifikasjonInnhold?.epostTittel shouldBe
                    document.varselInstruks.notifikasjonInnhold.epostTittel
                hendelse.data?.notifikasjonInnhold?.epostBody shouldBe
                    document.varselInstruks.notifikasjonInnhold.epostBody
                hendelse.data?.notifikasjonInnhold?.smsTekst shouldBe
                    document.varselInstruks.notifikasjonInnhold.smsTekst
            }
        }

        describe("serialization") {
            it("should serialize @type for esyfovarsel contract") {
                val jsonNode = createEsyfovarselObjectMapper().readTree(
                    createEsyfovarselObjectMapper().writeValueAsString(
                        ArbeidsgiverNotifikasjonTilAltinnRessursHendelse(
                            type = HendelseType.AG_VARSEL_ALTINN_RESSURS,
                            ferdigstill = false,
                            data = VarselData(
                                VarselDataNotifikasjonInnhold(
                                    epostTittel = "Tittel",
                                    epostBody = "Body",
                                    smsTekst = "Sms",
                                )
                            ),
                            arbeidstakerFnr = "12345678910",
                            eksternReferanseId = "external-id",
                            kilde = "syfo-dokumentporten",
                            orgnummer = "123456789",
                            ressursId = "nav_syfo_dialog",
                            ressursUrl = "https://www.altinn.no/messagebox",
                        )
                    )
                )

                jsonNode["@type"].asText() shouldBe "ArbeidsgiverNotifikasjonTilAltinnRessursHendelse"
                jsonNode["type"].asText() shouldBe "AG_VARSEL_ALTINN_RESSURS"
                jsonNode["data"]["notifikasjonInnhold"]["epostTittel"].asText() shouldBe "Tittel"
            }
        }
    })
