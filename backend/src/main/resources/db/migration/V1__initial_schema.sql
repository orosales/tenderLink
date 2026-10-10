-- TenderReady schema. Flyway runs this inside the configured schema (DB_SCHEMA, default "tender").

CREATE TABLE company (
    id                      UUID PRIMARY KEY,
    name                    VARCHAR(200) NOT NULL,
    contact_name            VARCHAR(200),
    services                JSONB        NOT NULL DEFAULT '[]',
    specializations         JSONB        NOT NULL DEFAULT '[]',
    service_areas           JSONB        NOT NULL DEFAULT '[]',
    capacity                JSONB        NOT NULL DEFAULT '[]',
    experience              TEXT,
    qualifications          JSONB        NOT NULL DEFAULT '[]',
    evidence_summary        TEXT,
    partnership_preference  TEXT,
    open_to_partnerships    BOOLEAN      NOT NULL DEFAULT FALSE,
    profile_confirmed_at    TIMESTAMPTZ,
    profile_revision        INTEGER      NOT NULL DEFAULT 1,
    is_demo                 BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tender (
    id                   UUID PRIMARY KEY,
    source               VARCHAR(40)  NOT NULL,
    source_id            VARCHAR(200) NOT NULL,
    amendment_number     VARCHAR(40),
    solicitation_number  VARCHAR(200),
    title                TEXT         NOT NULL,
    buyer                TEXT,
    buyer_address        TEXT,
    description          TEXT,
    category             TEXT,
    source_url           TEXT,
    delivery_regions_raw TEXT,
    delivery_areas       JSONB,
    attachments          JSONB        NOT NULL DEFAULT '[]',
    published_on         DATE,
    closing_at           TIMESTAMPTZ,
    source_timezone      VARCHAR(80),
    status               VARCHAR(80),
    fetched_at           TIMESTAMPTZ  NOT NULL,
    last_seen_at         TIMESTAMPTZ,
    content_hash         VARCHAR(64)  NOT NULL,
    is_demo              BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_tender_source UNIQUE (source, source_id)
);
CREATE INDEX ix_tender_closing ON tender (closing_at);

CREATE TABLE tender_document (
    id              UUID PRIMARY KEY,
    tender_id       UUID         NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    name            TEXT         NOT NULL,
    url             TEXT,
    extracted_text  TEXT,
    version_hash    VARCHAR(64),
    access_status   VARCHAR(40)  NOT NULL,
    access_note     TEXT,
    is_demo         BOOLEAN      NOT NULL DEFAULT FALSE,
    fetched_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_tender_document_tender ON tender_document (tender_id);

-- Company-independent interpretation of one document version (cached so reassessment does not re-call the AI).
CREATE TABLE document_extraction (
    id                UUID PRIMARY KEY,
    tender_id         UUID         NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    document_version  VARCHAR(64)  NOT NULL,
    mode              VARCHAR(20)  NOT NULL,
    model             VARCHAR(80),
    result            JSONB        NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_extraction UNIQUE (tender_id, document_version, mode)
);

CREATE TABLE requirement_correction (
    id                 UUID PRIMARY KEY,
    tender_id          UUID         NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    document_version   VARCHAR(64)  NOT NULL,
    requirement_id     VARCHAR(40)  NOT NULL,
    correction         JSONB        NOT NULL,
    corrected_by       UUID         REFERENCES company (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_correction UNIQUE (tender_id, document_version, requirement_id)
);

CREATE TABLE invitation (
    id                      UUID PRIMARY KEY,
    tender_id               UUID         NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    sender_company_id       UUID         NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    recipient_company_id    UUID         NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    proposed_contribution   JSONB        NOT NULL,
    message                 TEXT,
    state                   VARCHAR(40)  NOT NULL,
    sender_confirmed_at     TIMESTAMPTZ,
    recipient_confirmed_at  TIMESTAMPTZ,
    responded_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_invitation_not_self CHECK (sender_company_id <> recipient_company_id)
);
-- At most one active invitation per tender and company pair.
CREATE UNIQUE INDEX uq_invitation_active ON invitation (tender_id, sender_company_id, recipient_company_id)
    WHERE state IN ('INVITED', 'INTEREST_ACCEPTED', 'CONFIRMED_FOR_ASSESSMENT');

CREATE TABLE assessment (
    id                        UUID PRIMARY KEY,
    tender_id                 UUID         NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    company_id                UUID         NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    invitation_id             UUID         REFERENCES invitation (id) ON DELETE CASCADE,
    baseline_assessment_id    UUID         REFERENCES assessment (id) ON DELETE SET NULL,
    profile_revision          INTEGER      NOT NULL,
    partner_revisions         JSONB        NOT NULL DEFAULT '{}',
    document_id               UUID         REFERENCES tender_document (id) ON DELETE SET NULL,
    document_version          VARCHAR(64),
    mode                      VARCHAR(20)  NOT NULL,
    model                     VARCHAR(80),
    document_complete         BOOLEAN      NOT NULL,
    readiness                 VARCHAR(40)  NOT NULL,
    extraction                JSONB        NOT NULL,
    findings                  JSONB        NOT NULL,
    actions                   JSONB        NOT NULL,
    warnings                  JSONB        NOT NULL DEFAULT '[]',
    partnership_permission    VARCHAR(40)  NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_assessment_tender_company ON assessment (tender_id, company_id, created_at DESC);

CREATE TABLE import_run (
    id           UUID PRIMARY KEY,
    source       VARCHAR(40)  NOT NULL,
    url          TEXT         NOT NULL,
    trigger      VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    started_at   TIMESTAMPTZ  NOT NULL,
    finished_at  TIMESTAMPTZ,
    rows_read    INTEGER      NOT NULL DEFAULT 0,
    inserted     INTEGER      NOT NULL DEFAULT 0,
    updated      INTEGER      NOT NULL DEFAULT 0,
    unchanged    INTEGER      NOT NULL DEFAULT 0,
    skipped      INTEGER      NOT NULL DEFAULT 0,
    error        TEXT
);
