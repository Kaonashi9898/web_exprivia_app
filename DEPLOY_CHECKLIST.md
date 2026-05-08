# Deploy Checklist

Questa checklist raccoglie i punti da sistemare quando il progetto sara' pronto per un deploy reale.

Per ora il progetto puo' continuare a funzionare in locale senza applicare subito queste modifiche.

## Da fare prima del deploy

- Separare in modo chiaro la configurazione `dev` e `prod` mantenendo un solo codice applicativo.
- Usare un file `.env` locale non tracciato per sviluppo e un set di variabili/secret separato per produzione.
- Impostare default sicuri lato backend:
  - `ADMIN_BOOTSTRAP_ENABLED=false`
  - `auth.cookie.secure=true`
- Abilitare override espliciti solo in locale quando servono:
  - `ADMIN_BOOTSTRAP_ENABLED=true` solo per bootstrap iniziale
  - `auth.cookie.secure=false` solo su `localhost` senza HTTPS
- Rigenerare i segreti reali di runtime:
  - `JWT_SECRET`
  - password database
  - password admin bootstrap
- Verificare che `.env.example` contenga solo placeholder e nessun valore reale.
- Sostituire `frontend/Dockerfile` con una build multi-stage che esegua `ng build` e serva i file statici con Nginx o equivalente.
- Applicare la stessa logica di build statica anche al Dockerfile di `PlanimetriaEditor-main`, evitando `ng serve` nei container finali.
- Rivedere CORS, URL base, hostname e porte per ambiente di produzione.
- Verificare che il bootstrap admin sia trattato come flag one-shot e venga disattivato dopo l'inizializzazione.
- Estendere `frontend-verification.yml` con i test frontend reali quando il codice sara' piu' stabile.
- Aggiungere un workflow backend Maven per compile/test automatici quando i servizi saranno piu' assestati.

## Non urgente per il solo sviluppo locale

- Docker e compose specifici per produzione
- HTTPS reale e reverse proxy
- Hardening finale dei domini e delle policy cross-origin
