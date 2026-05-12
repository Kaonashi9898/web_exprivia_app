# prenotazioniExpBA v02 - note porte 13000

# Modifiche porte host - famiglia 13000

Questa variante mantiene invariate le porte interne dei container e cambia solo le porte pubblicate sull'host tramite Docker Compose.

## Mappatura

- `13010 -> frontend:4200`
- `13020 -> planimetria-editor:4201`
- `13030 -> utenti-service:8081`
- `13040 -> location-service:8082`
- `13050 -> prenotazioni-service:8083`
- `13060 -> rabbitmq:15672`
- `13061 -> rabbitmq:5672`
- `13070 -> postgres-utenti:5432`
- `13080 -> postgres-location:5432`
- `13090 -> postgres-prenotazioni:5432`

## File modificati

- `.env.example`: aggiunte variabili delle porte e `COMPOSE_PROJECT_NAME=prenotazioniexpba`.
- `docker-compose.yml`: tutte le sezioni `ports` usano variabili con default `130xx`.
- `frontend/src/environments/environment.development.ts`: URL di default aggiornati verso le porte host `130xx`.
- `backend/*-service/.../SecurityConfig.java`: aggiunte le origin CORS `localhost:13010`, `13020`, `13030`, `13040`, `13050`.
- `README.md`: riferimenti principali aggiornati.

## Avvio consigliato

```bash
cp .env.example .env
docker compose up --build -d
```

Poi aprire:

- Frontend: http://localhost:13010
- Editor planimetria: http://localhost:13020/editor
- RabbitMQ management: http://localhost:13060
