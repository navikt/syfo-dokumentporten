# syfo-dokumentporten

- `./gradlew build` runs build, tests and lint; `./gradlew test` runs tests.
- Local startup: `mise docker-up`, then `mise start`; compose supplies local
  dependencies including Texas and the fake auth server.
- Document access is checked for both the requested organisation and the
  document type's Altinn resource. Preserve the distinct user and system-user
  paths in `ValidationService`; a valid token alone does not grant access.
- System-user access via a parent organisation requires both a verified EREG
  hierarchy match and a PDP decision for that organisation/resource.
- Varsel publication happens before marking the instruction published in the
  database. Keep document UUIDs stable across retries for downstream
  deduplication and preserve permanent-error versus retryable-error handling.
- Documents contain health information. Keep PDF content and person
  identifiers out of ordinary logs and metric labels.
