package it.exprivia.location.messaging;

/**
 * Evento ricevuto da RabbitMQ quando utenti-service elimina un gruppo.
 *
 * Questo record corrisponde al messaggio JSON pubblicato da utenti-service.
 * Il location-service lo usa per eliminare tutte le associazioni gruppo-postazione
 * relative al gruppo eliminato.
 */
public record GruppoEliminatoEvent(Long gruppoId) {}
