# prenotazioniExpBA v04

## Nota v05 - pubblicazione Apache

La v05 corregge il comportamento del frontend quando l'applicazione viene aperta tramite dominio pubblico o reverse proxy Apache.
In accesso locale diretto resta possibile usare `http://localhost:13010`; in accesso pubblico il frontend usa path relativi e Apache instrada verso i tre microservizi.

Vedere anche:

- `APACHE_prenotazioniExpBA-8444.conf`
- `PUBLIC_EXPOSURE_NOTES_v05.md`


## Deploy rapido con script

Dopo aver copiato `prenotazioniExpBA_v04.zip` in `~/dockerITSBA`:

```bash
cd ~/dockerITSBA
unzip prenotazioniExpBA_v04.zip
chmod +x deploy.sh stop.sh
./deploy.sh 04
```

Lo ZIP contiene già `prenotazioniExpBA_v04/.env` configurato con la famiglia porte 13000.

URL principali:

- Frontend: `http://localhost:13010`
- Editor planimetria: `http://localhost:13020/editor`
- RabbitMQ management: `http://localhost:13060`

---

# prenotazioniExpBA v03

Applicazione per la prenotazione delle postazioni di lavoro Exprivia - versione laboratorio/demo.

## Deploy rapido

Gli script devono stare nella cartella `~/dockerITSBA`, fuori dalla cartella versionata.

```bash
cd ~/dockerITSBA
unzip prenotazioniExpBA_v03.zip
chmod +x deploy-prenotazioniExpBA.sh stop-prenotazioniExpBA.sh
./deploy-prenotazioniExpBA.sh 03
```

URL principali:

```text
Frontend:             http://localhost:13010
Editor planimetria:   http://localhost:13020/editor
RabbitMQ management:  http://localhost:13060
```

---

# prenotazioniExpBA v03

Prima versione rinominata del progetto di prenotazione postazioni Exprivia BA/ITS.

- ZIP: `prenotazioniExpBA_v03.zip`
- Cartella contenuta nello ZIP: `prenotazioniExpBA_v03/`
- Docker Compose project name: `prenotazioniexpba`
- Porte host dedicate: famiglia `13000`

> Nota: `COMPOSE_PROJECT_NAME` è scritto in minuscolo (`prenotazioniexpba`) perché Docker Compose richiede nomi progetto compatibili con lettere minuscole, numeri, trattini o underscore.


## Porte host dedicate - famiglia 13000

Questa versione espone l'applicazione sull'host usando la famiglia di porte `13000`, lasciando invariate le porte interne dei container.

| Componente | Porta host | Porta interna container |
| --- | ---: | ---: |
| Frontend principale | 13010 | 4200 |
| Editor planimetria | 13020 | 4201 |
| utenti-service | 13030 | 8081 |
| location-service | 13040 | 8082 |
| prenotazioni-service | 13050 | 8083 |
| RabbitMQ management | 13060 | 15672 |
| RabbitMQ AMQP | 13061 | 5672 |
| PostgreSQL utenti | 13070 | 5432 |
| PostgreSQL location | 13080 | 5432 |
| PostgreSQL prenotazioni | 13090 | 5432 |

Le porte PostgreSQL e RabbitMQ sono vincolate a `127.0.0.1` tramite `INFRA_BIND_ADDRESS`, quindi sono esposte solo localmente per debug.

# prenotazioniExpBA

Guida completa per avviare il progetto:
- da zero su una macchina dove Docker non e ancora configurato
- su una macchina dove Docker e gia pronto
- con credenziali di accesso alla piattaforma

## 1) Architettura e porte

Servizi principali:
- `frontend` (Angular): `http://localhost:13010`
- `PlanimetriaEditor` (Angular): `http://localhost:13020/editor`
- `utenti-service`: `http://localhost:13030`
- `location-service`: `http://localhost:13040`
- `prenotazioni-service`: `http://localhost:13050`
- `rabbitmq management`: `http://localhost:13060` (user/pass: `guest` / `guest`)

Database PostgreSQL:
- utenti DB: `localhost:13070`
- location DB: `localhost:13080`
- prenotazioni DB: `localhost:13090`

Nota importante:
- `docker compose` della root avvia backend + frontend + editor planimetria.

## 2) Credenziali di accesso alla piattaforma

Non esiste un admin di default hardcoded nel codice.  
Le credenziali di primo accesso sono quelle che imposti nel file `.env` tramite bootstrap:

- `ADMIN_BOOTSTRAP_EMAIL`
- `ADMIN_BOOTSTRAP_PASSWORD`

Esempio valido:

```env
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_BOOTSTRAP_FULL_NAME=Mario Rossi
ADMIN_BOOTSTRAP_EMAIL=mario.rossi@exprivia.com
ADMIN_BOOTSTRAP_PASSWORD=Password123!
```

Poi accedi dal frontend (`http://localhost:13010`) con:
- email: valore di `ADMIN_BOOTSTRAP_EMAIL`
- password: valore di `ADMIN_BOOTSTRAP_PASSWORD`

Vincolo email registrazione/login:
- il backend accetta formato `nome.cognome@exprivia.com`

## 3) Scenario A - Prima configurazione (Docker non configurato)

Questa sezione e pensata per macchina nuova o Docker Desktop mai configurato.

### 3.1 Prerequisiti da installare

Su Windows:
1. Installa Docker Desktop.
2. Abilita backend WSL2 quando richiesto da Docker Desktop.
3. Riavvia la macchina se richiesto.
4. Apri Docker Desktop e attendi stato "Engine running".

Verifica da PowerShell:

```powershell
docker --version
docker compose version
```

### 3.2 Configurazione progetto

Dalla root del repository:

```powershell
Copy-Item .env.example .env
```

Apri `.env` e imposta almeno questi valori:

```env
DB_USERNAME=postgres_user
DB_USERNAME_UTENTI=postgres_user
DB_USERNAME_LOCATION=postgres_user
DB_USERNAME_PRENOTAZIONI=postgres_user
DB_PASSWORD=una-password-sicura
JWT_SECRET=una-chiave-lunga-almeno-32-caratteri
JWT_EXPIRATION=86400000

ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_BOOTSTRAP_FULL_NAME=Mario Rossi
ADMIN_BOOTSTRAP_EMAIL=mario.rossi@exprivia.com
ADMIN_BOOTSTRAP_PASSWORD=Password123!
```

### 3.3 Primo avvio completo (backend + frontend + editor)

Da root:

```powershell
docker compose up --build -d
```

Controllo stato:

```powershell
docker compose ps
```

Log in tempo reale (opzionale):

```powershell
docker compose logs -f utenti-service location-service prenotazioni-service frontend planimetria-editor
```

### 3.4 Verifica accesso UI

Editor disponibile su:
- `http://localhost:13020/editor`

### 3.5 Primo login piattaforma

Apri:
- frontend: `http://localhost:13010`

Accedi con le credenziali bootstrap:
- email = `ADMIN_BOOTSTRAP_EMAIL`
- password = `ADMIN_BOOTSTRAP_PASSWORD`

Dopo il primo avvio consigliato:
1. rimetti `ADMIN_BOOTSTRAP_ENABLED=false` nel file `.env`
2. aggiorna solo utenti-service:

```powershell
docker compose up -d utenti-service
```

## 4) Scenario B - Docker gia configurato (avvio quotidiano)

Ogni volta che vuoi accendere tutto:

### 4.1 Avvia stack principale

Da root:

```powershell
docker compose up -d
```

Se hai modificato Dockerfile o dipendenze:

```powershell
docker compose up --build -d
```

## 5) Spegnimento, riavvio e reset

Spegnere senza eliminare container:

```powershell
docker compose stop
```

Spegnere e rimuovere container/rete:

```powershell
docker compose down
```

Spegnere e cancellare anche i volumi DB (reset totale dati):

```powershell
docker compose down -v
```

Riaccendere dopo stop/down:

```powershell
docker compose up -d
```

## 6) Checklist rapida

Per lavorare:
1. `docker compose up -d` (root)
2. Apri `http://localhost:13010`
3. (opzionale) Apri anche `http://localhost:13020/editor`
4. Login con credenziali admin del tuo `.env`

## 7) Troubleshooting veloce

### Porta occupata (13010, 13020, 13030, 13040, 13050)

Controlla processi in ascolto:

```powershell
Get-NetTCPConnection -LocalPort 13010,13020,13030,13040,13050 -ErrorAction SilentlyContinue
```

### Login fallito subito

Verifica:
- `ADMIN_BOOTSTRAP_ENABLED=true` al primo avvio
- email in formato `nome.cognome@exprivia.com`
- password uguale a `ADMIN_BOOTSTRAP_PASSWORD`
- log utenti-service:

```powershell
docker compose logs -f utenti-service
```

### Frontend su ma editor non raggiungibile

Controlla che il servizio sia in esecuzione:

```powershell
docker compose ps
docker compose logs -f planimetria-editor
```