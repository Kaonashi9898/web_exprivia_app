# Prenotazioni — Sistema di prenotazione postazioni

Sistema fullstack a microservizi per la gestione e prenotazione di postazioni aziendali.

test

---

## Microservizi

| Servizio | Porta | Database | Descrizione |
|---|---|---|---|
| `utenti-service` | `8081` | PostgreSQL `5432` | Autenticazione, utenti, gruppi |
| `location-service` | `8082` | PostgreSQL `5433` | Sedi, edifici, piani, stanze, postazioni, planimetrie |
| `prenotazioni-service` | `8083` | PostgreSQL `5434` | Prenotazioni orarie con vincoli anti-overlap |
| `notification-service` | `8084` | PostgreSQL `5435` | Notifiche, email *(da implementare)* |

---

## Tecnologie

| Cosa | Tecnologia |
|---|---|
| Linguaggio | Java 17 |
| Framework | Spring Boot 4 |
| Database | PostgreSQL 16 |
| Autenticazione | JWT (JSON Web Token) |
| ORM | Spring Data JPA / Hibernate |
| Build tool | Maven |
| Containerizzazione | Docker / Docker Compose |

---

## Ruoli

| Ruolo | Descrizione |
|---|---|
| `ADMIN` | Accesso completo a tutte le funzionalità |
| `BUILDING_MANAGER` | Gestione stato postazioni e gruppi-postazioni |
| `RECEPTION` | Visualizzazione postazioni prenotate |
| `USER` | Prenotazione postazioni (assegnato di default alla registrazione) |
| `GUEST` | Ospite con accesso limitato |

---

## Avvio

### Prerequisiti

- **Docker Desktop** installato e in esecuzione
- **Java 17** (solo per avvio locale senza Docker)
- **Maven** (solo per avvio locale senza Docker)

### 1. Crea il file `.env` nella root del progetto

```bash
cp .env.example .env
```

Modifica il valore di `JWT_SECRET` con una stringa di almeno 32 caratteri:

```env
DB_USERNAME=postgres_user
DB_USERNAME_UTENTI=postgres_user
DB_USERNAME_LOCATION=postgres_user
DB_USERNAME_PRENOTAZIONI=postgres_user
DB_PASSWORD=cambia-con-password-sicura
JWT_SECRET=cambia-con-una-chiave-sicura-di-almeno-32-caratteri
JWT_EXPIRATION=86400000
PLANIMETRIA_STORAGE_DIR=storage/planimetrie
ADMIN_BOOTSTRAP_ENABLED=false
ADMIN_BOOTSTRAP_FULL_NAME=System Admin
ADMIN_BOOTSTRAP_EMAIL=admin.bootstrap@exprivia.com
ADMIN_BOOTSTRAP_PASSWORD=cambia-questa-password-admin
```

`PLANIMETRIA_STORAGE_DIR` è opzionale e indica dove salvare immagini e JSON delle planimetrie.

`DB_USERNAME_UTENTI`, `DB_USERNAME_LOCATION` e `DB_USERNAME_PRENOTAZIONI` vengono usati da Docker Compose per i database separati.
`ADMIN_BOOTSTRAP_*` serve solo per creare il primo amministratore in modo controllato, poi va disattivato.

> Il file `.env` non viene committato su Git. Non condividerlo mai.

Per bootstrap del primo admin:
1. imposta `ADMIN_BOOTSTRAP_ENABLED=true`
2. valorizza `ADMIN_BOOTSTRAP_FULL_NAME`, `ADMIN_BOOTSTRAP_EMAIL` e `ADMIN_BOOTSTRAP_PASSWORD`
3. avvia `utenti-service`
4. verifica il login dell'admin creato
5. reimposta `ADMIN_BOOTSTRAP_ENABLED=false`

### 2. Avvia tutto con Docker Compose

```bash
docker-compose up --build
```

Questo avvia in ordine:
1. `postgres-utenti` (porta 5432)
2. `postgres-location` (porta 5433)
3. `rabbitmq` (porte 5672 e 15672)
4. `utenti-service` (porta 8081) — attende che DB e RabbitMQ siano pronti
5. `location-service` (porta 8082) — attende che DB e RabbitMQ siano pronti
6. `postgres-prenotazioni` (porta 5434)
7. `prenotazioni-service` (porta 8083) — attende che DB, RabbitMQ e i servizi dipendenti siano avviati

Per avviare in background:

```bash
docker-compose up --build -d
```

Per vedere i log di un singolo servizio:

```bash
docker-compose logs -f utenti-service
docker-compose logs -f location-service
docker-compose logs -f prenotazioni-service
```

---

### 3. Avvio locale (sviluppo, senza Docker)

Avvia prima i database con Docker:

```bash
docker-compose up postgres-utenti postgres-location postgres-prenotazioni rabbitmq -d
```

Poi avvia ciascun servizio dalla propria cartella.

**Linux / Mac:**
```bash
cd backend/utenti-service
./mvnw spring-boot:run
```

**Windows (PowerShell) — carica le variabili dal `.env` locale:**
```powershell
Get-Content .env | Where-Object { $_ -match '=' } | ForEach-Object {
    $n,$v = $_.split('=',2)
    [System.Environment]::SetEnvironmentVariable($n,$v)
}
mvn spring-boot:run
```

### Fermare i servizi

```bash
docker-compose down        # ferma i container, i dati rimangono
docker-compose down -v     # ferma e cancella anche i volumi (dati persi)
```

---

## Comunicazione tra servizi

Il token JWT generato da `utenti-service` viene validato autonomamente da ogni altro servizio con la stessa `JWT_SECRET`. Non è necessario contattare `utenti-service` per verificare un token.

Ogni richiesta autenticata deve includere l'header:

```
Authorization: Bearer <token>
```

Il token scade dopo **24 ore** (configurabile con `JWT_EXPIRATION` in millisecondi).

---

## Database

| Servizio | Container | Porta locale | Database | Credenziali |
|---|---|---|---|---|
| `utenti-service` | `postgres-utenti` | `5432` | `postgres` | `DB_USERNAME / DB_PASSWORD` |
| `location-service` | `postgres-location` | `5433` | `postgres` | `DB_USERNAME / DB_PASSWORD` |
| `prenotazioni-service` | `postgres-prenotazioni` | `5434` | `postgres` | `DB_USERNAME / DB_PASSWORD` |

Connessione diretta al DB (per debug):
```bash
docker exec -it postgres-utenti psql -U <DB_USERNAME> -d postgres
docker exec -it postgres-location psql -U <DB_USERNAME> -d postgres
docker exec -it postgres-prenotazioni psql -U <DB_USERNAME> -d postgres
```

---

## Formato degli errori

Tutti i servizi restituiscono gli errori con la stessa struttura:

```json
{
  "timestamp": "2026-04-16T08:00:00Z",
  "status": 400,
  "errore": "Descrizione del problema",
  "dettagli": {
    "campo": "Messaggio di validazione"
  }
}
```

`dettagli` Ã¨ presente solo quando l'errore contiene informazioni di validazione campo per campo.

| Codice HTTP | Quando |
|---|---|
| `400` | Input non valido o operazione non consentita |
| `401` | Token JWT mancante o non valido |
| `403` | Utente autenticato ma senza i permessi necessari |
| `404` | Risorsa non trovata |

---

---

# utenti-service — Endpoint

Base URL: `http://localhost:8081`

---

## POST /api/auth/register

Registra un nuovo utente. Pubblico, non richiede token.

**Body:**
```json
{
  "fullName": "Mario Rossi",
  "email": "mario.rossi@exprivia.com",
  "password": "password123",
  "ruolo": "USER"
}
```

Valori validi per `ruolo` nella registrazione pubblica: `USER`, `GUEST`

I ruoli privilegiati (`ADMIN`, `BUILDING_MANAGER`, `RECEPTION`) non possono essere assegnati da questo endpoint pubblico.
Devono essere assegnati successivamente da un amministratore gia' esistente.

**Risposta 200:**
```json
{
  "id": 1,
  "fullName": "Mario Rossi",
  "email": "mario.rossi@exprivia.com",
  "ruolo": "USER"
}
```

---

## POST /api/auth/login

Login. Pubblico, non richiede token. Restituisce il JWT da usare in tutte le chiamate successive.

**Body:**
```json
{
  "email": "mario.rossi@exprivia.com",
  "password": "password123"
}
```

**Risposta 200:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

Usa questo token nell'header di ogni chiamata successiva:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## GET /api/utenti/me

Profilo dell'utente corrente (ricavato dal token).

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
{
  "id": 1,
  "fullName": "Mario Rossi",
  "email": "mario.rossi@exprivia.com",
  "ruolo": "USER"
}
```

---

## GET /api/utenti

Lista di tutti gli utenti registrati.

**Grant:** `ADMIN`

**Risposta 200:**
```json
[
  { "id": 1, "fullName": "Mario Rossi", "email": "mario.rossi@exprivia.com", "ruolo": "USER" },
  { "id": 2, "fullName": "Admin", "email": "admin@exprivia.com", "ruolo": "ADMIN" }
]
```

---

## GET /api/utenti/{id}

Dettaglio di un utente per ID.

**Grant:** `ADMIN`

**Risposta 200:**
```json
{
  "id": 1,
  "fullName": "Mario Rossi",
  "email": "mario.rossi@exprivia.com",
  "ruolo": "USER"
}
```

---

## PUT /api/utenti/{id}

Aggiorna i dati anagrafici di un utente.

**Grant:** `ADMIN`

**Body:**
```json
{
  "fullName": "Mario Rossi",
  "email": "mario.rossi@exprivia.com"
}
```

**Risposta 200:** utente aggiornato

---

## PATCH /api/utenti/{id}/ruolo

Aggiorna solo il ruolo di un utente.

**Grant:** `ADMIN`

**Body:**
```json
{
  "ruolo": "BUILDING_MANAGER"
}
```

Valori validi: `ADMIN`, `BUILDING_MANAGER`, `RECEPTION`, `USER`, `GUEST`

**Risposta 200:** utente aggiornato

---

## DELETE /api/utenti/{id}

Elimina un utente.

**Grant:** `ADMIN`

**Risposta 204:** nessun corpo

---

## GET /api/gruppi/me

Gruppi a cui appartiene l'utente corrente (ricavato dal token).

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
[
  { "id": 1, "nome": "Team Backend" }
]
```

---

## GET /api/gruppi

Lista di tutti i gruppi.

**Grant:** `ADMIN`

**Risposta 200:**
```json
[
  { "id": 1, "nome": "Team Backend" },
  { "id": 2, "nome": "Team Frontend" }
]
```

---

## POST /api/gruppi?nome=NomeGruppo

Crea un nuovo gruppo. Il nome viene passato come query parameter.

**Grant:** `ADMIN`

**Esempio:**
```
POST /api/gruppi?nome=Team%20Backend
```

**Risposta 200:**
```json
{ "id": 3, "nome": "Team Backend" }
```

---

## DELETE /api/gruppi/{id}

Elimina un gruppo.

**Grant:** `ADMIN`

**Risposta 200:** gruppo eliminato

---

## POST /api/gruppi/{idGruppo}/utenti/{idUtente}

Aggiunge un utente a un gruppo.

**Grant:** `ADMIN`

**Risposta 200:** nessun corpo

---

## DELETE /api/gruppi/{idGruppo}/utenti/{idUtente}

Rimuove un utente da un gruppo.

**Grant:** `ADMIN`

**Risposta 204:** nessun corpo

---

## GET /api/gruppi/{idGruppo}/utenti

Lista degli utenti appartenenti a un gruppo.

**Grant:** `ADMIN`

**Risposta 200:**
```json
[
  { "id": 1, "fullName": "Mario Rossi", "email": "mario.rossi@exprivia.com", "ruolo": "USER" }
]
```

---

---

# location-service — Endpoint

Base URL: `http://localhost:8082`

Tutte le GET richiedono autenticazione. Le operazioni di scrittura richiedono ruoli specifici indicati per ogni endpoint.

---

## Sedi

### GET /api/sedi

Lista di tutte le sedi. Filtro opzionale per città.

**Grant:** qualsiasi utente autenticato

**Esempi:**
```
GET /api/sedi
GET /api/sedi?citta=Roma
```

**Risposta 200:**
```json
[
  {
    "id": 1,
    "nome": "Sede Roma",
    "indirizzo": "Via Tiburtina 1",
    "citta": "Roma",
    "latitudine": 41.903923,
    "longitudine": 12.513421
  }
]
```

---

### GET /api/sedi/{id}

Dettaglio di una sede.

**Grant:** qualsiasi utente autenticato

**Risposta 200:** sede singola (stesso formato della lista)

---

### POST /api/sedi

Crea una nuova sede.

**Grant:** `ADMIN`

**Body:**
```json
{
  "nome": "Sede Roma",
  "indirizzo": "Via Tiburtina 1",
  "citta": "Roma",
  "latitudine": 41.903923,
  "longitudine": 12.513421
}
```

`latitudine` e `longitudine` sono opzionali.

**Risposta 201:** sede creata

---

### PUT /api/sedi/{id}

Aggiorna una sede.

**Grant:** `ADMIN`

**Body:** stesso formato del POST

**Risposta 200:** sede aggiornata

---

### DELETE /api/sedi/{id}

Elimina una sede.

**Grant:** `ADMIN`

**Risposta 204:** nessun corpo

---

## Edifici

### GET /api/edifici/sede/{sedeId}

Lista degli edifici di una sede.

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
[
  {
    "id": 1,
    "nome": "Edificio A",
    "sedeId": 1,
    "sedeNome": "Sede Roma"
  }
]
```

---

### GET /api/edifici/{id}

Dettaglio di un edificio.

**Grant:** qualsiasi utente autenticato

---

### POST /api/edifici

Crea un edificio.

**Grant:** `ADMIN`

**Body:**
```json
{
  "nome": "Edificio A",
  "sedeId": 1
}
```

**Risposta 201:** edificio creato

---

### PUT /api/edifici/{id}

Aggiorna un edificio.

**Grant:** `ADMIN`

**Body:** stesso formato del POST

**Risposta 200:** edificio aggiornato

---

### DELETE /api/edifici/{id}

Elimina un edificio.

**Grant:** `ADMIN`

**Risposta 204:** nessun corpo

---

## Piani

### GET /api/piani/edificio/{edificioId}

Lista dei piani di un edificio.

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
[
  {
    "id": 1,
    "numero": 0,
    "edificioId": 1,
    "edificioNome": "Edificio A"
  }
]
```

---

### GET /api/piani/{id}

Dettaglio di un piano.

**Grant:** qualsiasi utente autenticato

---

### POST /api/piani

Crea un piano.

**Grant:** `ADMIN`

**Body:**
```json
{
  "numero": 0,
  "edificioId": 1
}
```

`numero` è l'indice del piano (0 = piano terra, 1 = primo piano, ecc.)

**Risposta 201:** piano creato

---

### PUT /api/piani/{id}

Aggiorna un piano.

**Grant:** `ADMIN`

**Body:** stesso formato del POST

**Risposta 200:** piano aggiornato

---

### DELETE /api/piani/{id}

Elimina un piano.

**Grant:** `ADMIN`

**Risposta 204:** nessun corpo

---

## Planimetrie

La planimetria è associata a un singolo piano. Il flusso attuale è composto da due passaggi separati:
- caricamento dell'immagine base della planimetria (`png`, `jpg/jpeg`, `svg`)
- import del file JSON esportato dall'editor Angular esterno

Il `location-service` salva entrambi i file su disco e sincronizza automaticamente `stanze` e `postazioni` in base al JSON importato.

Le coordinate di stanze e postazioni sono percentuali rispetto all'immagine (`0..100`), quindi:
- `x` rappresenta la posizione orizzontale percentuale
- `y` rappresenta la posizione verticale percentuale

### GET /api/piani/{pianoId}/planimetria

Restituisce i metadati della planimetria associata a un piano.

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
{
  "id": 10,
  "pianoId": 1,
  "imageName": "open-space-1-1712050000000.png",
  "formatoOriginale": "PNG",
  "coordXmin": 0,
  "coordXmax": 100,
  "coordYmin": 0,
  "coordYmax": 100,
  "imageUrl": "/api/piani/1/planimetria/image",
  "postazioniUrl": "/api/piani/1/planimetria/postazioni",
  "layoutUrl": "/api/piani/1/planimetria/layout"
}
```

---

### GET /api/piani/{pianoId}/planimetria/layout

Restituisce il layout completo salvato dal JSON dell'editor esterno.

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
{
  "exportedAt": "2026-04-09T10:00:00Z",
  "image": {
    "filename": "open-space.png",
    "naturalWidth": 1400,
    "naturalHeight": 900
  },
  "rooms": [
    {
      "id": "room_1",
      "label": "Open Space",
      "position": { "xPct": 10.5, "yPct": 20.25 },
      "stationIds": ["stn_1"]
    }
  ],
  "stations": [
    {
      "id": "stn_1",
      "label": "PDL-01",
      "position": { "xPct": 18.75, "yPct": 24.50 },
      "roomId": "room_1",
      "roomLabel": "Open Space"
    }
  ],
  "connections": [
    { "stationId": "stn_1", "roomId": "room_1" }
  ]
}
```

---

### GET /api/piani/{pianoId}/planimetria/postazioni

Restituisce le postazioni derivate dal JSON importato.

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
[
  {
    "id": "stn_1",
    "pdl": "PDL-01",
    "stanza": "Open Space",
    "x": 18.75,
    "y": 24.50
  }
]
```

---

### GET /api/piani/{pianoId}/planimetria/image

Restituisce l'immagine della planimetria salvata.

**Grant:** qualsiasi utente autenticato

**Risposta 200:** immagine `image/png`, `image/jpeg` oppure `image/svg+xml`

---

### POST /api/piani/{pianoId}/planimetria/image

Carica l'immagine base della planimetria come `multipart/form-data`.

**Grant:** `ADMIN`

**Body (`multipart/form-data`):**
- `file`: file `.png`, `.jpg`, `.jpeg` oppure `.svg`

**Risposta 201:** metadati della planimetria salvata

Effetti collaterali del caricamento:
- aggiorna o crea la `planimetria` del piano
- salva su disco l'immagine della planimetria
- non modifica ancora stanze e postazioni

**Esempio:**
```bash
curl -X POST http://localhost:8082/api/piani/1/planimetria/image \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@planimetria.png"
```

---

### POST /api/piani/{pianoId}/planimetria/json

Importa il file JSON esportato dall'editor esterno come `multipart/form-data`.

**Grant:** `ADMIN`

**Body (`multipart/form-data`):**
- `file`: file `.json` esportato dall'editor planimetrie

**Risposta 201:** metadati aggiornati della planimetria

Effetti collaterali dell'import:
- salva su disco il JSON della planimetria
- crea automaticamente le stanze mancanti
- crea o aggiorna automaticamente le postazioni importate

**Esempio:**
```bash
curl -X POST http://localhost:8082/api/piani/1/planimetria/json \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@floor-plan.json"
```

---

### DELETE /api/piani/{pianoId}/planimetria

Elimina la planimetria associata al piano e i file immagine/JSON salvati su disco.

**Grant:** `ADMIN`

**Risposta 204:** nessun corpo

---

## Stanze

### GET /api/stanze/piano/{pianoId}

Lista delle stanze di un piano.

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
[
  {
    "id": 1,
    "nome": "Sala Riunioni A",
    "pianoId": 1,
    "pianoNumero": 0
  }
]
```

---

### GET /api/stanze/{id}

Dettaglio di una stanza.

**Grant:** qualsiasi utente autenticato

---

### POST /api/stanze

Crea una stanza.

**Grant:** `ADMIN`

**Body:**
```json
{
  "nome": "Sala Riunioni A",
  "pianoId": 1
}
```

**Risposta 201:** stanza creata

---

### PUT /api/stanze/{id}

Aggiorna una stanza.

**Grant:** `ADMIN`

**Body:** stesso formato del POST

**Risposta 200:** stanza aggiornata

---

### DELETE /api/stanze/{id}

Elimina una stanza.

**Grant:** `ADMIN`

**Risposta 204:** nessun corpo

---

## Postazioni

**Tipi:** `OPEN_SPACE` · `SALA_RIUNIONI` · `UFFICIO_PRIVATO` · `LABORATORIO`

**Stati:** `DISPONIBILE` · `NON_DISPONIBILE` · `MANUTENZIONE` · `CAMBIO_DESTINAZIONE`

### GET /api/postazioni/stanza/{stanzaId}

Tutte le postazioni di una stanza.

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
[
  {
    "id": 1,
    "codice": "PS-001",
    "cadId": "CAD-XYZ",
    "tipo": "OPEN_SPACE",
    "stato": "DISPONIBILE",
    "accessibile": false,
    "x": 10.5,
    "y": 20.3,
    "stanzaId": 1,
    "stanzaNome": "Sala Riunioni A"
  }
]
```

---

### GET /api/postazioni/stanza/{stanzaId}/disponibili

Solo le postazioni con stato `DISPONIBILE` di una stanza.

**Grant:** qualsiasi utente autenticato

**Risposta 200:** lista di postazioni (stesso formato)

---

### GET /api/postazioni/{id}

Dettaglio di una postazione.

**Grant:** qualsiasi utente autenticato

---

### GET /api/postazioni/{id}/disponibile

Verifica se una postazione è disponibile. Usato internamente da `prenotazioni-service`.

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
true
```

---

### POST /api/postazioni

Crea una postazione.

**Grant:** `BUILDING_MANAGER` o `ADMIN`

**Body:**
```json
{
  "codice": "PS-001",
  "cadId": "CAD-XYZ",
  "tipo": "OPEN_SPACE",
  "stato": "DISPONIBILE",
  "accessibile": false,
  "x": 10.5,
  "y": 20.3,
  "stanzaId": 1
}
```

`cadId`, `stato`, `accessibile`, `x`, `y` sono opzionali. Se `stato` è omesso viene impostato `DISPONIBILE`. Se `accessibile` è omesso viene impostato `false`.

**Risposta 201:** postazione creata

---

### PUT /api/postazioni/{id}

Aggiorna una postazione (aggiornamento completo).

**Grant:** `BUILDING_MANAGER` o `ADMIN`

**Body:** stesso formato del POST (tutti i campi obbligatori)

**Risposta 200:** postazione aggiornata

---

### PATCH /api/postazioni/{id}/stato?stato=MANUTENZIONE

Cambia solo lo stato di una postazione, senza modificare gli altri campi.

**Grant:** `BUILDING_MANAGER` o `ADMIN`

**Esempio:**
```
PATCH /api/postazioni/1/stato?stato=MANUTENZIONE
PATCH /api/postazioni/1/stato?stato=DISPONIBILE
```

**Risposta 200:** postazione con stato aggiornato

---

### DELETE /api/postazioni/{id}

Elimina una postazione.

**Grant:** `BUILDING_MANAGER` o `ADMIN`

**Risposta 204:** nessun corpo

---

## Gruppi-Postazioni

Associa i gruppi utente (definiti in `utenti-service`) alle postazioni del `location-service`. La relazione è gestita a livello applicativo: `gruppoId` è un semplice intero, senza FK cross-database.

### GET /api/gruppi-postazioni/gruppo/{gruppoId}

Lista delle postazioni associate a un gruppo.

**Grant:** qualsiasi utente autenticato

**Risposta 200:**
```json
[
  {
    "id": 1,
    "gruppoId": 2,
    "postazioneId": 5,
    "postazioneCodice": "PS-001"
  }
]
```

---

### GET /api/gruppi-postazioni/postazione/{postazioneId}

Lista dei gruppi associati a una postazione.

**Grant:** qualsiasi utente autenticato

**Risposta 200:** lista (stesso formato)

---

### POST /api/gruppi-postazioni/gruppo/{gruppoId}/postazione/{postazioneId}

Associa un gruppo a una postazione.

**Grant:** `BUILDING_MANAGER` o `ADMIN`

**Esempio:**
```
POST /api/gruppi-postazioni/gruppo/2/postazione/5
```

**Risposta 201:** associazione creata

---

### DELETE /api/gruppi-postazioni/gruppo/{gruppoId}/postazione/{postazioneId}

Rimuove l'associazione tra un gruppo e una postazione.

**Grant:** `BUILDING_MANAGER` o `ADMIN`

**Risposta 204:** nessun corpo

---

## Flusso completo di test (curl / Postman)

Questo è l'ordine corretto per popolare il sistema da zero.

### Step 1 — Registra un admin

```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Admin","email":"admin@exprivia.com","password":"admin123","ruolo":"ADMIN"}'
```

### Step 2 — Login e salva il token

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@exprivia.com","password":"admin123"}'
```

Salva il valore di `token` dalla risposta. Nei passaggi successivi sostituisci `<TOKEN>` con questo valore.

### Step 3 — Crea una sede

```bash
curl -X POST http://localhost:8082/api/sedi \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Sede Roma","indirizzo":"Via Tiburtina 1","citta":"Roma"}'
```

Salva l'`id` dalla risposta (es. `1`).

### Step 4 — Crea un edificio

```bash
curl -X POST http://localhost:8082/api/edifici \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Edificio A","sedeId":1}'
```

### Step 5 — Crea un piano

```bash
curl -X POST http://localhost:8082/api/piani \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"numero":0,"edificioId":1}'
```

### Step 6 — Crea una stanza

```bash
curl -X POST http://localhost:8082/api/stanze \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Open Space","pianoId":1}'
```

### Step 7 — Crea una postazione

```bash
curl -X POST http://localhost:8082/api/postazioni \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"codice":"PS-001","tipo":"OPEN_SPACE","stanzaId":1}'
```

### Step 8 — Verifica le postazioni disponibili

```bash
curl http://localhost:8082/api/postazioni/stanza/1/disponibili \
  -H "Authorization: Bearer <TOKEN>"
```

### Step 9 — Cambia lo stato di una postazione

```bash
curl -X PATCH "http://localhost:8082/api/postazioni/1/stato?stato=MANUTENZIONE" \
  -H "Authorization: Bearer <TOKEN>"
```

### Step 10 — Carica l'immagine della planimetria del piano

```bash
curl -X POST http://localhost:8082/api/piani/1/planimetria/image \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@planimetria.png"
```

### Step 11 — Importa il JSON esportato dall'editor planimetrie

```bash
curl -X POST http://localhost:8082/api/piani/1/planimetria/json \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@floor-plan.json"
```

### Step 12 — Leggi metadati, layout e postazioni importate dalla planimetria

```bash
curl http://localhost:8082/api/piani/1/planimetria \
  -H "Authorization: Bearer <TOKEN>"

curl http://localhost:8082/api/piani/1/planimetria/layout \
  -H "Authorization: Bearer <TOKEN>"

curl http://localhost:8082/api/piani/1/planimetria/postazioni \
  -H "Authorization: Bearer <TOKEN>"
```

---

---

## Convenzione TODO

Per lasciare note nel codice usa il prefisso `TODO:`:

```java
// TODO: implementare la validazione dell'email aziendale
```

Per trovare tutti i TODO in VS Code: apri la ricerca (`Ctrl+Shift+F`) e cerca `TODO:`.
