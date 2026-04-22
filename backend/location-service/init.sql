CREATE TYPE tipo_postazione AS ENUM ('OPEN_SPACE', 'SALA_RIUNIONI', 'UFFICIO_PRIVATO', 'LABORATORIO');
CREATE TYPE stato_postazione AS ENUM ('DISPONIBILE', 'NON_DISPONIBILE', 'MANUTENZIONE', 'CAMBIO_DESTINAZIONE');
CREATE TYPE formato_file AS ENUM ('PNG', 'JPEG', 'SVG', 'DWG', 'DXF');

CREATE TABLE sede (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(100) NOT NULL,
    indirizzo   VARCHAR(200) NOT NULL,
    citta       VARCHAR(100) NOT NULL,
    latitudine  DECIMAL(9,6),
    longitudine DECIMAL(9,6)
);

CREATE TABLE edificio (
    id      BIGSERIAL PRIMARY KEY,
    nome    VARCHAR(100) NOT NULL,
    sede_id BIGINT NOT NULL REFERENCES sede(id)
);

CREATE TABLE piano (
    id           BIGSERIAL PRIMARY KEY,
    numero       INTEGER NOT NULL,
    nome         VARCHAR(100),
    edificio_id  BIGINT NOT NULL REFERENCES edificio(id)
);

CREATE TABLE stanza (
    id       BIGSERIAL PRIMARY KEY,
    nome     VARCHAR(100) NOT NULL,
    piano_id BIGINT NOT NULL REFERENCES piano(id),
    CONSTRAINT uk_stanza_nome_piano UNIQUE (nome, piano_id)
);

CREATE TABLE postazione (
    id          BIGSERIAL PRIMARY KEY,
    codice      VARCHAR(50)      NOT NULL UNIQUE,
    cad_id      VARCHAR(50),
    tipo        tipo_postazione  NOT NULL,
    stato       stato_postazione NOT NULL DEFAULT 'DISPONIBILE',
    accessibile BOOLEAN          NOT NULL DEFAULT FALSE,
    x           DECIMAL(12,2),
    y           DECIMAL(12,2),
    stanza_id   BIGINT NOT NULL REFERENCES stanza(id)
);

CREATE TABLE gruppo_postazione (
    id            BIGSERIAL PRIMARY KEY,
    gruppo_id     BIGINT NOT NULL,
    postazione_id BIGINT NOT NULL REFERENCES postazione(id) ON DELETE CASCADE,
    CONSTRAINT uk_gruppo_postazione UNIQUE (gruppo_id, postazione_id)
);

CREATE TABLE planimetria (
    id                   BIGSERIAL PRIMARY KEY,
    file_originale_path  VARCHAR(500),
    png_path             VARCHAR(500),
    json_path            VARCHAR(500),
    image_name           VARCHAR(255),
    coord_xmin           DECIMAL(12,2),
    coord_xmax           DECIMAL(12,2),
    coord_ymin           DECIMAL(12,2),
    coord_ymax           DECIMAL(12,2),
    formato_originale    formato_file,
    piano_id             BIGINT NOT NULL UNIQUE REFERENCES piano(id)
);

INSERT INTO sede (nome, indirizzo, citta) VALUES
('Exprivia - Roma Bufalotta', 'Via della Bufalotta 378', 'Roma'),
('Exprivia - Molfetta Headquarter', 'Via A. Olivetti 11', 'Molfetta'),
('Exprivia - Molfetta Agnelli', 'Via Giovanni Agnelli 5', 'Molfetta'),
('Exprivia - Milano', 'Via dei Valtorta 43', 'Milano'),
('Exprivia - Lecce', 'Campus Ecotekne, c/o Edificio Dhitech, Via Monteroni 165', 'Lecce'),
('Exprivia - Matera', 'Via Giovanni Agnelli snc', 'Matera'),
('Exprivia - Palermo', 'Viale Regione Siciliana Nord-Ovest 7275', 'Palermo'),
('Exprivia - Trento', 'Palazzo Stella, Via Alcide De Gasperi 77', 'Trento'),
('Exprivia - Vicenza', 'Via L. Lazzaro Zamenhof 817', 'Vicenza');

INSERT INTO edificio (nome, sede_id)
SELECT 'Edificio principale', id
FROM sede;
