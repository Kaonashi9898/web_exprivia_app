# prenotazioniExpBA v05

Versione derivata dalla v04.

## Modifiche principali

- Corretto comportamento frontend per accesso pubblico dietro Apache:
  - su `localhost` conserva le chiamate dirette a `localhost:13030`, `13040`, `13050`;
  - su dominio pubblico/LAN tramite Apache usa path relativi:
    - `/api-utenti`
    - `/api-location`
    - `/api-prenotazioni`
    - `/editor/`
- Predisposto l'editor planimetria per essere servito sotto `/editor/`.
- Aggiunto file di esempio Apache:
  - `APACHE_prenotazioniExpBA-8444.conf`
- Aggiunte note operative:
  - `PUBLIC_EXPOSURE_NOTES_v05.md`

## Porte host confermate

- Frontend: `13010`
- Editor planimetria: `13020`
- utenti-service: `13030`
- location-service: `13040`
- prenotazioni-service: `13050`
- RabbitMQ Management: `13060`
- RabbitMQ AMQP: `13061`
- PostgreSQL utenti: `13070`
- PostgreSQL location: `13080`
- PostgreSQL prenotazioni: `13090`
