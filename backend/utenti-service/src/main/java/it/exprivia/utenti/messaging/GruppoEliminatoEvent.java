package it.exprivia.utenti.messaging;

/**
 * Evento pubblicato su RabbitMQ quando un gruppo viene eliminato.
 *
 * Questo record viene serializzato in JSON e inviato agli altri microservizi
 * (es. location-service) che devono reagire all'eliminazione del gruppo.
 * Contiene solo l'ID del gruppo eliminato.
 */
public record GruppoEliminatoEvent(Long gruppoId) {}
