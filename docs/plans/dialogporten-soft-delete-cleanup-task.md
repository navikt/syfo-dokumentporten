# Plan: Rydd Dialogporten-lenker for soft-deleted transmissions

## Mål

Implementere en leader-elected background task som periodisk finner dokumenter som er soft-deleted i `syfo-dokumentporten`, men som ikke ennå er ryddet i Dialogporten, og utløper/skjuler relevante Dialogporten-lenker i en liten, kontrollert arbeidsrunde.

Planen gjelder issue `navikt/syfo-dokumentporten#202`.

## Klassifisering

**Risiko: R4 kritisk**

Endringen berører:

- databaseschema og data-state
- ekstern write-operasjon mot Dialogporten
- Maskinporten/Altinn-scope
- retry/idempotens
- logging og metrikker rundt data som kan knyttes til personer

## Scope

### I scope

- Ny leader-elected background task.
- Kandidatuttrekk for dokumenter med `document.delete_performed IS NOT NULL`.
- Persistert cleanup-state for idempotens, retry og observability.
- Dialogporten-klientoperasjon for å sette/oppdatere `expiredAt`/`expiresAt` på relevant attachment/transmission.
- Radvis claim med retry-backoff.
- Metrikker og logging uten PII.
- Tester på migrering/DAO/service/client/task.

### Ikke i scope

- Selve soft delete-mekanismen fra issue `#201`.
- Hard delete av dokumentinnhold eller rader.
- Endring av eksisterende document API-responser.
- Endring av aktiv send-flyt til Dialogporten.
- Sletting av hele Dialogporten-dialoger.
- Nye inbound-endepunkter.

## Anbefalt datamodell

Bruk eksplisitt cleanup-state på `document`, ikke `DocumentStatus`. Cleanup er en egen livssyklus ved siden av send-status.

Anbefalt nye kolonner:

```sql
dialogporten_link_cleanup_performed TIMESTAMPTZ DEFAULT null,
dialogporten_link_cleanup_attempted TIMESTAMPTZ DEFAULT null
```

### Hvorfor ikke bare én timestamp?

Én `dialogporten_link_cleanup_performed`-kolonne er nok for enkel idempotens, men ikke for trygg claim/retry. Den løser ikke:

- overlappende kjøringer ved leader-skifte eller rolling deploy
- kontrollert retry hvis Dialogporten feiler
- å unngå at samme rad plukkes igjen umiddelbart etter en feil

`cleanup_attempted` brukes som en tidsbegrenset lease/retry-markør. Den betyr ikke "låst for alltid". Hvis jobben dør, kan raden plukkes opp igjen når `cleanup_attempted` er eldre enn valgt retry-timeout.

Resultat lagres ikke i databasen. Resultater rapporteres via logger og metrikker med lav kardinalitet.

### Hvorfor ikke markere `skipped_no_link`?

Dokumenter uten `transmission_id` eller `dialogporten_uuid` har ingen kjent Dialogporten-lenke å rydde. De bør derfor ekskluderes fra kandidatspørringen i stedet for å markeres som ryddet.

Hvis teamet ønsker å følge med på slike rader, bør det gjøres som en separat datakvalitetsmetrikk eller loggoppsummering, ikke som cleanup-resultat.

## Kandidatuttrekk og claim

Tasken skal claime én rad om gangen med et atomisk databasekall. Hver kjøring kan prosessere opptil et lavt maksantall rader.

Konseptuelt kandidatfilter:

```sql
delete_performed IS NOT NULL
AND dialogporten_link_cleanup_performed IS NULL
AND transmission_id IS NOT NULL
AND dialog.dialogporten_uuid IS NOT NULL
AND (
    dialogporten_link_cleanup_attempted IS NULL
    OR dialogporten_link_cleanup_attempted < now() - <retry_backoff>
)
```

Claim bør skje med ett atomisk databasekall som setter `dialogporten_link_cleanup_attempted = now()` og returnerer én rad. Dette kan gjøres med `FOR UPDATE SKIP LOCKED` eller tilsvarende `UPDATE ... RETURNING`, slik at to podder ikke prosesserer samme dokument samtidig ved leader-skifte.

Viktig: Ikke hold en databasetransaksjon åpen mens eksternt Dialogporten-kall pågår. Claim raden kort, commit, kall Dialogporten, og marker deretter success.

## Dataflyt

1. Task våkner på intervall.
2. Task sjekker `leaderElection.isLeader()`.
3. Hvis podden ikke er leder: vent til neste intervall.
4. Leder forsøker å claime én kandidat.
5. Hvis ingen kandidat finnes: avslutt arbeidsrunden og vent til neste intervall.
6. For claimed dokument:
   - Kall Dialogporten for å utløpe/skjule lenken.
   - Ved suksess: sett `dialogporten_link_cleanup_performed = now()`.
   - Ved idempotent "finnes ikke / allerede borte": sett `dialogporten_link_cleanup_performed = now()`.
   - Ved feil: ikke sett `cleanup_performed`; behold `cleanup_attempted` slik at raden kan prøves igjen etter retry-timeout.
7. Gjenta fra steg 4 til maks antall rader per kjøring er nådd, eller til ingen kandidater finnes.
8. Logg oppsummering for arbeidsrunden uten PII.
9. Oppdater metrikker.
10. Vent til neste intervall.

## Arbeidsrunde- og retry-strategi

Anbefalt første versjon:

- Intervall: 10 minutter.
- Claim én rad om gangen.
- Prosesser maks 10 rader per kjøring.
- Retry-timeout for `cleanup_attempted`: start med 30 minutter.

Dette holder løsningen enkel, begrenser last mot Dialogporten og unngår at samme feilede rad prosesseres igjen umiddelbart.

Hvis initial backlog viser seg stor, kan maksantallet per kjøring økes senere uten å endre datamodellen.

## Dialogporten-kontrakt

Må avklares før implementering:

- Hvilket HTTP-verb skal brukes mot `serviceowner/dialogs/{dialogId}/transmissions/{transmissionId}`?
- Skal payload være full transmission, partial update, JSON Patch eller attachment-spesifikk body?
- Hvordan identifiseres attachment hvis en transmission har flere attachments?
- Heter feltet `expiredAt` eller `expiresAt` i update-kontrakten?
- Skal verdien settes til `delete_performed`, `Instant.now()` eller en annen verdi?
- Hvilket scope kreves: eksisterende Dialogporten-scope eller nytt `changetransmission`-scope fra PR `#196`?
- Hvilke HTTP-statuskoder betyr idempotent success?

Planen bør ikke låse klient-signaturen før dette er bekreftet. Den endelige klientmetoden bør reflektere faktisk kontrakt, ikke antakelsen om at transmission alene er nok.

## Foreslåtte komponenter

| Komponent                      | Ansvar                                                                    |
| ------------------------------ | ------------------------------------------------------------------------- |
| `DocumentDAO`                  | Claime én cleanup-kandidat, markere cleanup utført, telle backlog         |
| `DialogportenClient`           | Utføre verifisert Dialogporten update-operasjon                           |
| `FakeDialogportenClient`       | Lokal/no-op eller deterministisk fake for tester/lokal kjøring            |
| `DialogportenCleanupService`   | Orkestrere arbeidsrunde, per-dokument feilisolasjon, metrikker og logging |
| `CleanupDialogportenLinksTask` | Leader election, intervall, graceful cancellation                         |
| `DialogportenMetrics`          | Cleanup counters, backlog gauge og ev. timer                              |

## Flyway-plan

Bruk **neste ledige Flyway-versjon ved implementering**. Ikke hardkod `V13`/`V14`, siden parallelle PR-er kan legge til migreringer.

### Migrering 1: cleanup-state

Legg til cleanup-kolonnene på `document`.

Eksempel:

```sql
ALTER TABLE document
    ADD COLUMN dialogporten_link_cleanup_performed TIMESTAMPTZ DEFAULT null,
    ADD COLUMN dialogporten_link_cleanup_attempted TIMESTAMPTZ DEFAULT null;
```

### Migrering 2: indeks, hvis nødvendig

Vurder volum før indeks legges til. Hvis tabellen er stor eller backlog-spørringen blir viktig, legg indeksen i egen migrering.

Indeks-predicate må begrenses til faktisk backlog:

```sql
-- flyway:executeInTransaction=false
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_document_dialogporten_cleanup_backlog
    ON document (delete_performed)
    WHERE delete_performed IS NOT NULL
      AND transmission_id IS NOT NULL
      AND dialogporten_link_cleanup_performed IS NULL;
```

Hvis `CREATE INDEX CONCURRENTLY` brukes, må migreringen ligge alene og Flyway-oppsettet verifiseres for non-transactional execution. En indeks kan ikke direkte referere til `dialog.dialogporten_uuid`; filtreringen på `dialogporten_uuid IS NOT NULL` skjer derfor i kandidatspørringen via join.

## Observability

Følg eksisterende mønster i `DialogportenMetrics.kt`, men bruk bedre navn på nye metrikker.

Foreslåtte metrikker:

| Metrikk                                                     | Type    | Labels                        | Formål                                                                        |
| ----------------------------------------------------------- | ------- | ----------------------------- | ----------------------------------------------------------------------------- |
| `syfo_dokumentporten_dialogporten_link_cleanup_total`       | Counter | `result`                      | Teller cleanup-resultater                                                     |
| `syfo_dokumentporten_dialogporten_link_cleanup_error_total` | Counter | `reason` med lav kardinalitet | Teller tekniske feil                                                          |
| `syfo_dokumentporten_dialogporten_link_cleanup_backlog`     | Gauge   | ingen                         | Antall soft-deleted dokumenter med Dialogporten-referanse som mangler cleanup |
| `syfo_dokumentporten_dialogporten_link_cleanup_run_seconds` | Timer   | ingen                         | Varighet per arbeidsrunde                                                     |

Tillatte labelverdier bør være faste, for eksempel:

- `result=expired`
- `result=already_expired`
- `reason=client_error`
- `reason=server_error`
- `reason=unexpected_error`

Ikke bruk `document_id`, `transmission_id`, `dialog_id`, `fnr`, rå URL eller trace-id som metric labels.

## Logging

Logg arbeidsrundenivå:

- antall kandidater claimed
- antall utløpt
- antall idempotent already expired
- antall feil
- eldste `delete_performed` i arbeidsrunden

Unngå standardlogging av:

- fnr
- navn
- dokumentinnhold
- tokens
- rå dokumentlenker
- rå Dialogporten-lenker
- høykardinalitetsidentifikatorer som default

Hvis feilsøking krever identifikator, avklar med security champion om intern DB-id kan logges på warn/error.

## Sikkerhet og personvern

- Ingen nye secrets i kode.
- Token hentes via eksisterende Texas/Maskinporten/Altinn-flyt.
- Verifiser at PR `#196` eller tilsvarende endring dekker nødvendig scope.
- Verifiser at eksisterende NAIS outbound/accessPolicy dekker Dialogporten/Altinn-kall.
- Ingen ny inbound.
- Alle SQL-spørringer skal være parameteriserte.
- Vurder om ny write-operasjon mot Dialogporten bør nevnes i behandlingsoversikt eller avklares med security champion.
- Ikke logg PII eller lenker som kan gi tilgang til dokumenter.

## Testplan

Testene skal skrives i samme fase som produksjonskoden de dekker. Ikke samle DAO-, klient-, service- og task-tester i en egen testfase til slutt.

### DAO

- Returnerer bare dokumenter med `delete_performed IS NOT NULL`.
- Returnerer bare dokumenter med både `transmission_id` og `dialogporten_uuid`.
- Ekskluderer dokumenter der cleanup allerede er utført.
- Respekterer retry-backoff via `cleanup_attempted`.
- Claimer én rad atomisk og unngår dobbeltclaim.
- Marker cleanup utført uten å endre `delete_performed` eller `transmission_id`.
- Backlog-count stemmer.

### Dialogporten-klient

- Bruker korrekt endpoint, HTTP-verb og payload etter avklart kontrakt.
- Bruker korrekt Altinn-scope.
- Mapper success til success.
- Mapper avklarte idempotente statuskoder til success.
- Mapper reelle 4xx/5xx til feil som gir retry.
- Logger ikke token, raw URL eller PII.

### Service

- Dokument uten `transmission_id` blir ikke claimed.
- Dokument uten `dialogporten_uuid` blir ikke claimed.
- Dokument med komplett Dialogporten-referanse kaller klient og markeres `expired`.
- Idempotent "already gone" markeres `already_expired`.
- Klientfeil på ett dokument stopper ikke resten av arbeidsrunden.
- Feilede dokumenter får ikke `cleanup_performed`.
- Metrikker økes med forventede labels.

### Task

- Ikke-leder prosesserer ikke.
- Leder claimer én rad om gangen og prosesserer maks 10 rader per wake-up.
- `CancellationException` håndteres som normal shutdown.
- Task wires i Koin og stoppes i `ApplicationStopPreparing`.

### PII/logg

- Loggmeldinger i cleanup-flyten inneholder ikke fnr, navn, rå lenker eller dokumentinnhold.
- Metric labels har lav kardinalitet og ingen persondata.

## Implementasjonsfaser

### Fase 1: Avklar ekstern kontrakt

**Filer:** ingen kodeendring nødvendigvis.

Avklar Dialogporten update-kontrakt, scope og idempotente statuskoder. Dette må gjøres før klient og DTO lages.

### Fase 2: Database-state

**Filer:**

- `src/main/resources/db/migration/V{next}__document_dialogporten_cleanup_state.sql`
- eventuelt `src/main/resources/db/migration/V{next}__document_dialogporten_cleanup_backlog_index.sql`
- eksisterende migrerings-/databasetester hvis prosjektet har en test som verifiserer Flyway-migreringer

Legg til cleanup-state og eventuell indeks. Fasen inkluderer test eller lokal verifisering av at migreringene kjører rent mot testdatabasen.

### Fase 3: DAO og domene

**Filer:**

- `src/main/kotlin/no/nav/syfo/document/db/DocumentDAO.kt`
- `src/main/kotlin/no/nav/syfo/document/db/DocumentEntity.kt`
- `src/test/kotlin/no/nav/syfo/document/db/DocumentDbTest.kt` eller tilsvarende DAO-test

Legg til claim, markering, backlog-count og entity-mapping.

Skriv DAO-testene i denne fasen:

- kandidatspørringen returnerer bare soft-deleted dokumenter med både `transmission_id` og `dialogporten_uuid`
- allerede ryddede dokumenter ekskluderes
- `cleanup_attempted` respekterer retry-timeout
- claim henter én rad atomisk
- markering av cleanup utført endrer ikke `delete_performed` eller `transmission_id`
- backlog-count stemmer

### Fase 4: Dialogporten-klient

**Filer:**

- `src/main/kotlin/no/nav/syfo/altinn/dialogporten/client/DialogportenClient.kt`
- `src/main/kotlin/no/nav/syfo/altinn/dialogporten/client/FakeDialogportenClient.kt`
- eventuelle DTO-er under `src/main/kotlin/no/nav/syfo/altinn/dialogporten/domain/`
- klienttester under `src/test/kotlin/no/nav/syfo/dialogporten/` eller eksisterende klient-testpakke

Legg til verifisert update-operasjon.

Skriv klienttestene i denne fasen:

- korrekt endpoint, HTTP-verb og payload etter avklart Dialogporten-kontrakt
- korrekt Altinn-scope
- success mappes til success
- avklarte idempotente statuskoder mappes til success
- reelle 4xx/5xx gir feil som service kan retrye
- token, rå URL og PII havner ikke i logg

### Fase 5: Cleanup service

**Filer:**

- ny `src/main/kotlin/no/nav/syfo/altinn/dialogporten/service/DialogportenCleanupService.kt`
- `src/main/kotlin/no/nav/syfo/altinn/dialogporten/DialogportenMetrics.kt`
- servicetester under `src/test/kotlin/no/nav/syfo/dialogporten/service/` eller eksisterende service-testpakke

Orkestrer arbeidsrunde, retry/backoff, success-markering, metrikker og logging.

Skriv servicetestene i denne fasen:

- dokument uten `transmission_id` blir ikke claimed
- dokument uten `dialogporten_uuid` blir ikke claimed
- dokument med komplett Dialogporten-referanse kaller klient og markeres ryddet
- idempotent "already gone" markeres ryddet
- klientfeil på ett dokument stopper ikke resten av arbeidsrunden
- feilede dokumenter får ikke `cleanup_performed`
- metrikker økes med forventede labels
- loggmeldinger inneholder ikke fnr, navn, rå lenker eller dokumentinnhold

### Fase 6: Task og wiring

**Filer:**

- ny `src/main/kotlin/no/nav/syfo/altinn/dialogporten/task/CleanupDialogportenLinksTask.kt`
- `src/main/kotlin/no/nav/syfo/plugins/DependencyInjection.kt`
- `src/main/kotlin/no/nav/syfo/plugins/ConfigureTasks.kt`
- task-/wiring-tester under `src/test/kotlin/no/nav/syfo/dialogporten/task/` eller eksisterende task-testpakke

Wire task med leader election og graceful shutdown.

Skriv task-testene i denne fasen:

- ikke-leder prosesserer ikke
- leder claimer én rad om gangen og prosesserer maks 10 rader per wake-up
- `CancellationException` håndteres som normal shutdown
- tasken wires i Koin og stoppes i `ApplicationStopPreparing`

### Fase 7: Samlet verifisering og dokumentasjon

**Filer:**

- `README.md` hvis teamet ønsker runtime-oppgaven dokumentert permanent

Kjør hele testpakken og build etter at fasene over har lagt inn sine tester: `./gradlew test` og `./gradlew build`. Denne fasen skal ikke være stedet der hovedtestene først skrives; den skal fange integrasjonsfeil og dokumentere tasken ved behov.

## Akseptansekriterier mapping

| Issue-krav                                                 | Planlagt dekning                                     |
| ---------------------------------------------------------- | ---------------------------------------------------- |
| Avklart og implementert måte å sette/oppdatere `expiredAt` | Fase 1 og 4                                          |
| Lenker for soft-deleted transmissions utløper/skjules      | Fase 3-6                                             |
| Aktive transmissions og aktive lenker påvirkes ikke        | Kandidatfilter krever `delete_performed IS NOT NULL` |
| Feil synliggjøres uten PII                                 | Observability-, logging- og sikkerhetsseksjonene     |
| Periodisk og trygg prosessering                            | Leader election, atomic row-claim og retry-backoff   |

## Åpne avklaringer før implementering

1. Hvilken Dialogporten update-kontrakt gjelder for attachment expiry?
2. Hvilket scope kreves, og er PR `#196` merget?
3. Hvilke Dialogporten-statuskoder skal tolkes som idempotent success?
4. Hvilken tid skal settes som expiry: `delete_performed`, `Instant.now()` eller annen verdi?
5. Skal intern DB-id kunne logges ved feil, eller skal alle identifikatorer utelates?
6. Er forventet volum stort nok til at partial-indeks trengs fra første release?
7. Er maks 10 rader per 10-minutters kjøring nok for forventet backlog, eller bør dette konfigureres fra miljø?
8. Finnes det en planlagt oppgave for å lage API-/servicekall som soft-deleter dokumenter, slik at cleanup senere kan trigges fra applikasjonsflyten i tillegg til periodisk reconciliation?

## Anbefaling

Start med to cleanup-kolonner, atomic row-claim og maks 10 rader per 10-minutters kjøring. Det gir en enkel nok løsning, men unngår de viktigste driftssviktene: permanent låste rader ved jobb-død, tight retry-loop, dobbeltprosessering ved leader-skifte og uendelig re-prosessering av dokumenter uten Dialogporten-lenke.
