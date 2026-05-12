# Deploy prenotazioniExpBA

Questa versione è pensata per avere gli script **fuori** dalla cartella versionata, direttamente in `~/dockerITSBA`.

Struttura attesa dopo l'unzip in `~/dockerITSBA`:

```text
~/dockerITSBA/
├── deploy-prenotazioniExpBA.sh
├── stop-prenotazioniExpBA.sh
├── prenotazioniExpBA.env              # creato al primo deploy
├── .prenotazioniExpBA-current         # creato dopo il deploy
├── prenotazioniExpBA_v03.zip
└── prenotazioniExpBA_v03/
    ├── docker-compose.yml
    ├── .env
    └── ...
```

## Primo deploy

```bash
cd ~/dockerITSBA
unzip prenotazioniExpBA_v03.zip
chmod +x deploy-prenotazioniExpBA.sh stop-prenotazioniExpBA.sh
./deploy-prenotazioniExpBA.sh 03
```

## Deploy successivi

Copiare il nuovo ZIP nella stessa cartella e lanciare, per esempio:

```bash
cd ~/dockerITSBA
./deploy-prenotazioniExpBA.sh 04
```

Lo script ferma la versione precedente e avvia quella nuova.

## File `.env`

Il file `.env` incluso nello ZIP serve come configurazione iniziale.
Al primo deploy viene copiato in:

```text
~/dockerITSBA/prenotazioniExpBA.env
```

Da quel momento la configurazione persistente è quella esterna alla cartella versionata, così non viene persa passando da una versione all'altra.
