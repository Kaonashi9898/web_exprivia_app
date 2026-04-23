# Webapp Exprivia Prenotazioni

Guida completa per avviare il progetto:
- da zero su una macchina dove Docker non e ancora configurato
- su una macchina dove Docker e gia pronto
- con credenziali di accesso alla piattaforma

## 1) Architettura e porte

Servizi principali:
- `frontend` (Angular): `http://localhost:4200`
- `PlanimetriaEditor` (Angular): `http://localhost:4201/editor`
- `utenti-service`: `http://localhost:8081`
- `location-service`: `http://localhost:8082`
- `prenotazioni-service`: `http://localhost:8083`
- `rabbitmq management`: `http://localhost:15672` (user/pass: `guest` / `guest`)

Database PostgreSQL:
- utenti DB: `localhost:5432`
- location DB: `localhost:5433`
- prenotazioni DB: `localhost:5434`

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

Poi accedi dal frontend (`http://localhost:4200`) con:
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
- `http://localhost:4201/editor`

### 3.5 Primo login piattaforma

Apri:
- frontend: `http://localhost:4200`

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
2. Apri `http://localhost:4200`
3. (opzionale) Apri anche `http://localhost:4201/editor`
4. Login con credenziali admin del tuo `.env`

## 7) Troubleshooting veloce

### Porta occupata (4200, 4201, 8081, 8082, 8083)

Controlla processi in ascolto:

```powershell
Get-NetTCPConnection -LocalPort 4200,4201,8081,8082,8083 -ErrorAction SilentlyContinue
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
