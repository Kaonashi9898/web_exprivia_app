CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TYPE stato_prenotazione AS ENUM ('CONFERMATA', 'ANNULLATA');

CREATE TABLE prenotazione (
    id                BIGSERIAL PRIMARY KEY,
    utente_id         BIGINT NOT NULL,
    utente_email      VARCHAR(150) NOT NULL,
    utente_full_name  VARCHAR(150) NOT NULL,
    postazione_id     BIGINT NOT NULL,
    postazione_codice VARCHAR(80) NOT NULL,
    stanza_id         BIGINT NOT NULL,
    stanza_nome       VARCHAR(120) NOT NULL,
    data_prenotazione DATE NOT NULL,
    ora_inizio        TIME NOT NULL,
    ora_fine          TIME NOT NULL,
    stato             stato_prenotazione NOT NULL DEFAULT 'CONFERMATA',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    intervallo        TSRANGE GENERATED ALWAYS AS (
        tsrange(
            (data_prenotazione + ora_inizio),
            (data_prenotazione + ora_fine),
            '[)'
        )
    ) STORED,
    CONSTRAINT ck_prenotazione_orari CHECK (ora_inizio < ora_fine)
);

CREATE UNIQUE INDEX uk_prenotazione_utente_giorno_attiva
    ON prenotazione (utente_id, data_prenotazione)
    WHERE stato = 'CONFERMATA';

ALTER TABLE prenotazione
    ADD CONSTRAINT ex_prenotazione_postazione_intervallo_attivo
    EXCLUDE USING GIST (
        postazione_id WITH =,
        intervallo WITH &&
    )
    WHERE (stato = 'CONFERMATA');

CREATE INDEX idx_prenotazione_postazione_data
    ON prenotazione (postazione_id, data_prenotazione);

CREATE INDEX idx_prenotazione_utente
    ON prenotazione (utente_id);

CREATE OR REPLACE FUNCTION set_prenotazione_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prenotazione_updated_at
BEFORE UPDATE ON prenotazione
FOR EACH ROW
EXECUTE FUNCTION set_prenotazione_updated_at();
