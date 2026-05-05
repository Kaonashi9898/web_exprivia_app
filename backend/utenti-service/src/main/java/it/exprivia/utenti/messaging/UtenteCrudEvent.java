package it.exprivia.utenti.messaging;

import it.exprivia.utenti.entity.RuoloUtente;

import java.time.Instant;

public record UtenteCrudEvent(
        String operazione,
        Long utenteId,
        String fullName,
        String email,
        RuoloUtente ruolo,
        String actorEmail,
        Instant occurredAt
) {}
