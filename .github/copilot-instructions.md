# syfo-dokumentporten



## Team
- **Team**: team-esyfo, NAV IT
- **Org**: navikt

## Commands

```bash
./gradlew build   # Build + test + lint
./gradlew test    # Tests only
```

## NAV Principles
- **Team First**: Autonomous teams with circles of autonomy
- **Product Development**: Continuous development over ad hoc approaches
- **Essential Complexity**: Focus on essential, avoid accidental complexity
- **DORA Metrics**: Measure and improve team performance

## Platform & Auth
- **Platform**: NAIS (Kubernetes on GCP)
- **Auth**: Azure AD (internal users), TokenX (on-behalf-of token exchange), ID-porten (citizens), Maskinporten (machine-to-machine)
- **Observability**: Prometheus metrics, Grafana Loki logs, Tempo tracing (OpenTelemetry)

## Conventions
- English code and comments — Norwegian for user-facing text and domain terms (e.g. dialogmote, sykmelding, oppfolgingsplan)
- Use Context7 (`context7-resolve-library-id` → `context7-query-docs`) for library-specific patterns (not available for NAV-internal libs like Aksel/NAIS — use aksel.nav.no and doc.nais.io instead)
- Check existing code patterns in the repository before writing new code
- Follow the ✅ Always / ⚠️ Ask First / 🚫 Never boundaries in agent and instruction files

## Documentation

Keep temporary notes outside the repository unless an existing ignored workspace is configured.
Maintain durable service documentation in `README.md` and any established domain documentation as
part of the authorized change. Record an ADR for a lasting architectural
tradeoff or a change to an earlier architectural decision, following existing
ADR paths and numbering when present. The task scope determines which docs
need updating; ask only when a material decision or authority is missing.

## Repository guidance

This repository owns `.github/copilot-instructions.md`, applicable files under
`.github/instructions/`, and retained local agents and skills. Update affected
repository guidance together with an authorized change, preserving service
facts, build commands, data rules, and operational constraints.

Portable agents and task workflows come from the selected nav-pilot package.
Use the exact component identities offered by the active session. Check local
and user components for name collisions when a skill is missing or resolves to
unexpected content.

## Tech Stack
- **Language**: Kotlin
- **Framework**: Ktor
- **Build**: Gradle (Kotlin DSL)
- **Database**: N/A
- **Messaging**: N/A
- **Testing**: Kotest, MockK
- **Auth**: Les NAIS-manifestene i prosjektet for å finne hvilke auth-mekanismer som er konfigurert (mulige: Azure AD, TokenX, ID-porten, Maskinporten)

## Backend Patterns
- Check `build.gradle.kts` for actual dependencies before suggesting libraries
- Use Flyway for all database migrations — never modify existing migrations
- Parameterized queries always — never string interpolation in SQL
- Follow the existing data access pattern in the repository (extension functions, repositories, etc.)
- Structured logging — check which pattern this repo uses (KotlinLogging, SLF4J, kv() fields, MDC)
- Follow existing code patterns in the repository

## Boundaries

### ✅ Always
- Run `./gradlew build` after changes
- Use Flyway for database migrations
- Add Prometheus metrics for business operations
- Validate JWT issuer, audience, and expiration

### ⚠️ Ask First
- Changing database schema or Kafka event schemas
- Modifying authentication configuration
- Adding new GCP resources

### 🚫 Never
- Skip database migration versioning
- Hardcode secrets or configuration values
- Use `!!` operator without null checks
- Bypass authentication checks
