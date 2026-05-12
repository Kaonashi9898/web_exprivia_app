CREATE TYPE status_utente AS ENUM ('CREATO', 'MODIFICATO', 'ELIMINATO');
CREATE TYPE ruolo_utente AS ENUM ('ADMIN', 'BUILDING_MANAGER', 'RECEPTION', 'USER', 'GUEST');
CREATE TYPE oauth_provider AS ENUM ('GOOGLE', 'MICROSOFT');
CREATE TYPE password_reset_request_status AS ENUM ('OPEN', 'DONE', 'REJECTED');

CREATE TABLE utenti (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(50) NOT NULL,
    email           VARCHAR(50) NOT NULL UNIQUE,
    password_hash   VARCHAR(120),
    ruolo           ruolo_utente NOT NULL DEFAULT 'USER'
);

CREATE TABLE sessioni (
    id              BIGSERIAL PRIMARY KEY,
    id_utente       BIGINT NOT NULL REFERENCES utenti(id) ON DELETE CASCADE,
    refresh_token   TEXT NOT NULL UNIQUE,
    scadenza        TIMESTAMPTZ NOT NULL,
    data_creazione  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE oauth_providers (
    id              BIGSERIAL PRIMARY KEY,
    id_utente       BIGINT NOT NULL REFERENCES utenti(id) ON DELETE CASCADE,
    provider        oauth_provider NOT NULL,
    provider_id     TEXT NOT NULL,
    access_token    TEXT,
    UNIQUE (provider, provider_id)
);

CREATE TABLE log_utenti (
    id              BIGSERIAL PRIMARY KEY,
    id_utente       BIGINT NOT NULL REFERENCES utenti(id) ON DELETE CASCADE,
    data_ora        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status_utente   status_utente NOT NULL
);

CREATE TABLE gruppi (
    id      BIGSERIAL PRIMARY KEY,
    nome    VARCHAR(50) NOT NULL
);

CREATE TABLE gruppi_utente (
    id          BIGSERIAL PRIMARY KEY,
    id_utente   BIGINT NOT NULL REFERENCES utenti(id) ON DELETE CASCADE,
    id_gruppo   BIGINT NOT NULL REFERENCES gruppi(id) ON DELETE CASCADE
);

CREATE TABLE password_reset_requests (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(50) NOT NULL,
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status              password_reset_request_status NOT NULL DEFAULT 'OPEN',
    handled_by_email    VARCHAR(50),
    handled_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_password_reset_requests_open_email
    ON password_reset_requests (email)
    WHERE status = 'OPEN';

CREATE INDEX ix_password_reset_requests_status_requested_at
    ON password_reset_requests (status, requested_at);
