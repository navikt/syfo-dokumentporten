CREATE TABLE varsel_instruks (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT      NOT NULL UNIQUE REFERENCES document(id),
    type            TEXT        NOT NULL,
    epost_tittel    TEXT        NOT NULL,
    epost_body      TEXT        NOT NULL,
    sms_tekst       TEXT        NOT NULL,
    ressurs_id      TEXT        NOT NULL,
    ressurs_url     TEXT        NOT NULL,
    kilde           TEXT        NOT NULL,
    created         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
