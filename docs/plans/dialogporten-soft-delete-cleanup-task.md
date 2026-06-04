# Plan: Rydd Dialogporten-lenker for soft-deleted transmissions

## Mål

Implementere en leader-elected background task som periodisk finner dokumenter som er soft-deleted i `syfo-dokumentporten`, men som ikke ennå er ryddet i Dialogporten, og utløper/skjuler relevante Dialogporten-lenker i små, trygge batcher.

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
- Små batcher med retry-backoff.
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
dialogporten_link_cleanup_attempted TIMESTAMPTZ DEFAULT null,
dialogporten_link_cleanup_result TEXT DEFAULT null
```

### Hvorfor ikke bare én timestamp?

Én `dialogporten_link_expired`-kolonne er nok for enkel idempotens, men ikke nok for robust drift. Den løser ikke:

- tight retry-loop når hele batchen feiler
- overlappende kjøringer ved leader-skifte eller rolling deploy
- skille mellom faktisk ryddet lenke og "ingenting å rydde"

`cleanup_attempted` gir retry-backoff. `cleanup_performed` er success-markør. `cleanup_result` gir enkel feilsøking uten å måtte logge identifikatorer.

Foreslåtte `cleanup_result`-verdier:

| Verdi             | Betydning                                                                   |
| ----------------- | --------------------------------------------------------------------------- |
| `expired`         | Lenken ble utløpt/skjult i Dialogporten                                     |
| `skipped_no_link` | Dokumentet hadde ikke nødvendig `transmission_id` eller `dialogporten_uuid` |
| `already_expired` | Dialogporten svarte at lenken/transmission allerede var borte eller utløpt  |

Feil trenger ikke lagres som result for første versjon. Ved feil beholdes `cleanup_performed = NULL`, mens `cleanup_attempted` settes slik at retry skjer etter backoff.

## Kandidatuttrekk og claim

Tasken skal ikke bare hente kandidater og prosessere dem direkte. Den bør først gjøre en atomisk claim av en batch.

Konseptuelt kandidatfilter:

```sql
delete_performed IS NOT NULL
AND dialogporten_link_cleanup_performed IS NULL
AND (
    dialogporten_link_cleanup_attempted IS NULL
    OR dialogporten_link_cleanup_attempted < now() - <retry_backoff>
)
```

Claim bør skje med ett atomisk databasekall, for eksempel med `FOR UPDATE SKIP LOCKED` eller tilsvarende `UPDATE ... RETURNING`, slik at to podder ikke prosesserer samme dokument samtidig ved leader-skifte.

Viktig: Ikke hold en databasetransaksjon åpen mens eksternt Dialogporten-kall pågår. Claim radene kort, commit, kall Dialogporten, og marker deretter success per dokument.

## Dataflyt

1. Task våkner på intervall.
2. Task sjekker `leaderElection.isLeader()`.
3. Hvis podden ikke er leder: vent til neste intervall.
4. Leder claimer én liten batch kandidater.
5. For hvert dokument i batchen:
   - Hvis `transmission_id` eller `dialog.dialogporten_uuid` mangler: marker cleanup som utført med `skipped_no_link`.
   - Ellers kall Dialogporten for å utløpe/skjule lenken.
   - Ved suksess: marker cleanup som utført med `expired`.
   - Ved idempotent "finnes ikke / allerede borte": marker cleanup som utført med `already_expired`.
   - Ved feil: ikke sett `cleanup_performed`; behold `cleanup_attempted` slik at retry skjer etter backoff.
6. Logg batchoppsummering uten PII.
7. Oppdater metrikker.
8. Vent til neste intervall.

## Batch- og retry-strategi

Anbefalt første versjon:

- Prosesser maks én claimed batch per wake-up.
- Batchstørrelse: start med 50 eller 100.
- Retry-backoff: start med 15-30 minutter.
- Intervall: start med 5 minutter, samme størrelsesorden som eksisterende `SendDialogTask`.

Dette unngår at samme feilede batch prosesseres igjen umiddelbart i en tight loop.

Hvis initial backlog viser seg stor, kan man senere utvide med maks N batcher per runde, men bare hvis claim/backoff hindrer re-prosessering av samme feilede rader.

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

| Komponent                      | Ansvar                                                             |
| ------------------------------ | ------------------------------------------------------------------ |
| `DocumentDAO`                  | Claime cleanup-kandidater, markere cleanup utført, telle backlog   |
| `DialogportenClient`           | Utføre verifisert Dialogporten update-operasjon                    |
| `FakeDialogportenClient`       | Lokal/no-op eller deterministisk fake for tester/lokal kjøring     |
| `DialogportenCleanupService`   | Orkestrere batch, per-dokument feilisolasjon, metrikker og logging |
| `CleanupDialogportenLinksTask` | Leader election, intervall, graceful cancellation                  |
| `DialogportenMetrics`          | Cleanup counters, backlog gauge og ev. timer                       |

## Flyway-plan

Bruk **neste ledige Flyway-versjon ved implementering**. Ikke hardkod `V13`/`V14`, siden parallelle PR-er kan legge til migreringer.

### Migrering 1: cleanup-state

Legg til cleanup-kolonnene på `document`.

Eksempel:

```sql
ALTER TABLE document
    ADD COLUMN dialogporten_link_cleanup_performed TIMESTAMPTZ DEFAULT null,
    ADD COLUMN dialogporten_link_cleanup_attempted TIMESTAMPTZ DEFAULT null,
    ADD COLUMN dialogporten_link_cleanup_result TEXT DEFAULT null;
```

### Migrering 2: indeks, hvis nødvendig

Vurder volum før indeks legges til. Hvis tabellen er stor eller backlog-spørringen blir viktig, legg indeksen i egen migrering.

Indeks-predicate må begrenses til faktisk backlog:

```sql
-- flyway:executeInTransaction=false
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_document_dialogporten_cleanup_backlog
    ON document (delete_performed)
    WHERE delete_performed IS NOT NULL
      AND dialogporten_link_cleanup_performed IS NULL;
```

Hvis `CREATE INDEX CONCURRENTLY` brukes, må migreringen ligge alene og Flyway-oppsettet verifiseres for non-transactional execution.

## Observability

Følg eksisterende mønster i `DialogportenMetrics.kt`, men bruk bedre navn på nye metrikker.

Foreslåtte metrikker:

| Metrikk                                                       | Type    | Labels                        | Formål                                             |
| ------------------------------------------------------------- | ------- | ----------------------------- | -------------------------------------------------- |
| `syfo_dokumentporten_dialogporten_link_cleanup_total`         | Counter | `result`                      | Teller cleanup-resultater                          |
| `syfo_dokumentporten_dialogporten_link_cleanup_error_total`   | Counter | `reason` med lav kardinalitet | Teller tekniske feil                               |
| `syfo_dokumentporten_dialogporten_link_cleanup_backlog`       | Gauge   | ingen                         | Antall soft-deleted dokumenter som mangler cleanup |
| `syfo_dokumentporten_dialogporten_link_cleanup_batch_seconds` | Timer   | ingen eller `result`          | Varighet per batch                                 |

Tillatte labelverdier bør være faste, for eksempel:

- `result=expired`
- `result=skipped_no_link`
- `result=already_expired`
- `reason=client_error`
- `reason=server_error`
- `reason=unexpected_error`

Ikke bruk `document_id`, `transmission_id`, `dialog_id`, `fnr`, rå URL eller trace-id som metric labels.

## Logging

Logg batchnivå:

- antall kandidater claimed
- antall utløpt
- antall skipped
- antall idempotent already expired
- antall feil
- eldste `delete_performed` i batchen

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

### DAO

- Returnerer bare dokumenter med `delete_performed IS NOT NULL`.
- Ekskluderer dokumenter der cleanup allerede er utført.
- Respekterer retry-backoff via `cleanup_attempted`.
- Claimer batch atomisk og unngår dobbeltclaim.
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

- Dokument uten `transmission_id` markeres `skipped_no_link`.
- Dokument uten `dialogporten_uuid` markeres `skipped_no_link`.
- Dokument med komplett Dialogporten-referanse kaller klient og markeres `expired`.
- Idempotent "already gone" markeres `already_expired`.
- Klientfeil på ett dokument stopper ikke resten av batchen.
- Feilede dokumenter får ikke `cleanup_performed`.
- Metrikker økes med forventede labels.

### Task

- Ikke-leder prosesserer ikke.
- Leder prosesserer én batch per wake-up.
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

Legg til cleanup-state og eventuell indeks.

### Fase 3: DAO og domene

**Filer:**

- `src/main/kotlin/no/nav/syfo/document/db/DocumentDAO.kt`
- `src/main/kotlin/no/nav/syfo/document/db/DocumentEntity.kt`
- relevante DAO-tester

Legg til claim, markering, backlog-count og entity-mapping.

### Fase 4: Dialogporten-klient

**Filer:**

- `src/main/kotlin/no/nav/syfo/altinn/dialogporten/client/DialogportenClient.kt`
- `src/main/kotlin/no/nav/syfo/altinn/dialogporten/client/FakeDialogportenClient.kt`
- eventuelle DTO-er under `src/main/kotlin/no/nav/syfo/altinn/dialogporten/domain/`

Legg til verifisert update-operasjon.

### Fase 5: Cleanup service

**Filer:**

- ny `src/main/kotlin/no/nav/syfo/altinn/dialogporten/service/DialogportenCleanupService.kt`
- `src/main/kotlin/no/nav/syfo/altinn/dialogporten/DialogportenMetrics.kt`

Orkestrer batch, retry/backoff, result-markering, metrikker og logging.

### Fase 6: Task og wiring

**Filer:**

- ny `src/main/kotlin/no/nav/syfo/altinn/dialogporten/task/CleanupDialogportenLinksTask.kt`
- `src/main/kotlin/no/nav/syfo/plugins/DependencyInjection.kt`
- `src/main/kotlin/no/nav/syfo/plugins/ConfigureTasks.kt`

Wire task med leader election og graceful shutdown.

### Fase 7: Tester og dokumentasjon

**Filer:**

- DAO-/service-/client-/task-tester under `src/test/kotlin`
- `README.md` hvis teamet ønsker runtime-oppgaven dokumentert permanent

Kjør `./gradlew test` og `./gradlew build`.

## Akseptansekriterier mapping

| Issue-krav                                                 | Planlagt dekning                                      |
| ---------------------------------------------------------- | ----------------------------------------------------- |
| Avklart og implementert måte å sette/oppdatere `expiredAt` | Fase 1 og 4                                           |
| Lenker for soft-deleted transmissions utløper/skjules      | Fase 3-6                                              |
| Aktive transmissions og aktive lenker påvirkes ikke        | Kandidatfilter krever `delete_performed IS NOT NULL`  |
| Feil synliggjøres uten PII                                 | Observability-, logging- og sikkerhetsseksjonene      |
| Periodisk og trygg prosessering                            | Leader election, atomic claim, batch og retry-backoff |

## Åpne avklaringer før implementering

1. Hvilken Dialogporten update-kontrakt gjelder for attachment expiry?
2. Hvilket scope kreves, og er PR `#196` merget?
3. Hvilke Dialogporten-statuskoder skal tolkes som idempotent success?
4. Hvilken tid skal settes som expiry: `delete_performed`, `Instant.now()` eller annen verdi?
5. Skal intern DB-id kunne logges ved feil, eller skal alle identifikatorer utelates?
6. Er forventet volum stort nok til at partial-indeks trengs fra første release?
7. Skal cleanup-resultat lagres som fri `TEXT`, check constraint eller egen enum? Første anbefaling er `TEXT` med applikasjonskontrollerte verdier for å unngå enum-migreringskompleksitet.

## Anbefaling

Start med databasebasert cleanup-state, atomic claim og maks én batch per wake-up. Det gir en enkel nok løsning, men unngår de viktigste driftssviktene: tight retry-loop, dobbeltprosessering ved leader-skifte og uendelig re-prosessering av dokumenter uten Dialogporten-lenke.
