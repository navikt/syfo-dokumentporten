CREATE TYPE arbeidsgiver_varsel_type AS ENUM ('INNKALT', 'AVLYST', 'NYTT_TID_STED', 'REFERAT');

CREATE TABLE varsel_instruks (
    id         BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES document(id),
    varsel_type arbeidsgiver_varsel_type NOT NULL,
    created    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id)
);
