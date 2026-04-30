CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TYPE stato_prenotazione AS ENUM ('CONFERMATA', 'ANNULLATA');
CREATE TYPE tipo_risorsa_prenotata AS ENUM ('POSTAZIONE', 'MEETING_ROOM');

CREATE TABLE prenotazione (
    id                BIGSERIAL PRIMARY KEY,
    utente_id         BIGINT NOT NULL,
    utente_email      VARCHAR(150) NOT NULL,
    utente_full_name  VARCHAR(150) NOT NULL,
    tipo_risorsa_prenotata tipo_risorsa_prenotata NOT NULL DEFAULT 'POSTAZIONE',
    postazione_id     BIGINT,
    postazione_codice VARCHAR(80),
    meeting_room_stanza_id BIGINT,
    meeting_room_nome VARCHAR(120),
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
    CONSTRAINT ck_prenotazione_orari CHECK (ora_inizio < ora_fine),
    CONSTRAINT ck_prenotazione_risorsa_singola CHECK (
        (
            tipo_risorsa_prenotata = 'POSTAZIONE'
            AND postazione_id IS NOT NULL
            AND postazione_codice IS NOT NULL
            AND meeting_room_stanza_id IS NULL
            AND meeting_room_nome IS NULL
        )
        OR
        (
            tipo_risorsa_prenotata = 'MEETING_ROOM'
            AND postazione_id IS NULL
            AND postazione_codice IS NULL
            AND meeting_room_stanza_id IS NOT NULL
            AND meeting_room_nome IS NOT NULL
        )
    )
);

ALTER TABLE prenotazione
    ADD CONSTRAINT ex_prenotazione_utente_intervallo_attivo
    EXCLUDE USING GIST (
        utente_id WITH =,
        intervallo WITH &&
    )
    WHERE (stato = 'CONFERMATA');

ALTER TABLE prenotazione
    ADD CONSTRAINT ex_prenotazione_postazione_intervallo_attivo
    EXCLUDE USING GIST (
        postazione_id WITH =,
        intervallo WITH &&
    )
    WHERE (stato = 'CONFERMATA' AND tipo_risorsa_prenotata = 'POSTAZIONE');

ALTER TABLE prenotazione
    ADD CONSTRAINT ex_prenotazione_meeting_room_intervallo_attivo
    EXCLUDE USING GIST (
        meeting_room_stanza_id WITH =,
        intervallo WITH &&
    )
    WHERE (stato = 'CONFERMATA' AND tipo_risorsa_prenotata = 'MEETING_ROOM');

CREATE INDEX idx_prenotazione_postazione_data
    ON prenotazione (postazione_id, data_prenotazione);

CREATE INDEX idx_prenotazione_meeting_room_data
    ON prenotazione (meeting_room_stanza_id, data_prenotazione);

CREATE INDEX idx_prenotazione_utente
    ON prenotazione (utente_id);

CREATE TABLE prenotazione_utente_gruppo_cache (
    utente_id   BIGINT NOT NULL,
    gruppo_id   BIGINT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (utente_id, gruppo_id)
);

CREATE TABLE prenotazione_postazione_gruppo_cache (
    postazione_id BIGINT NOT NULL,
    gruppo_id     BIGINT NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (postazione_id, gruppo_id)
);

CREATE INDEX idx_prenotazione_utente_gruppo_cache_utente
    ON prenotazione_utente_gruppo_cache (utente_id);

CREATE INDEX idx_prenotazione_postazione_gruppo_cache_postazione
    ON prenotazione_postazione_gruppo_cache (postazione_id);

CREATE OR REPLACE FUNCTION set_prenotazione_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION verify_prenotazione_group_access()
RETURNS TRIGGER AS $$
DECLARE
    postazione_has_groups BOOLEAN;
    shared_group_exists BOOLEAN;
BEGIN
    IF NEW.tipo_risorsa_prenotata <> 'POSTAZIONE' OR NEW.postazione_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM prenotazione_postazione_gruppo_cache pg
        WHERE pg.postazione_id = NEW.postazione_id
    )
    INTO postazione_has_groups;

    IF NOT postazione_has_groups THEN
        RETURN NEW;
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM prenotazione_postazione_gruppo_cache pg
        JOIN prenotazione_utente_gruppo_cache ug ON ug.gruppo_id = pg.gruppo_id
        WHERE pg.postazione_id = NEW.postazione_id
          AND ug.utente_id = NEW.utente_id
    )
    INTO shared_group_exists;

    IF NOT shared_group_exists THEN
        RAISE EXCEPTION 'Utente % non autorizzato a prenotare la postazione %: nessun gruppo condiviso',
            NEW.utente_id,
            NEW.postazione_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prenotazione_updated_at
BEFORE UPDATE ON prenotazione
FOR EACH ROW
EXECUTE FUNCTION set_prenotazione_updated_at();

CREATE TRIGGER trg_prenotazione_group_access
BEFORE INSERT OR UPDATE OF utente_id, postazione_id, tipo_risorsa_prenotata
ON prenotazione
FOR EACH ROW
EXECUTE FUNCTION verify_prenotazione_group_access();
