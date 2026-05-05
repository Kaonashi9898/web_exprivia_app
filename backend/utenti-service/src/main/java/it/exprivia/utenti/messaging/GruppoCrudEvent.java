package it.exprivia.utenti.messaging;

import java.time.Instant;

public record GruppoCrudEvent(
        String operazione,
        Long gruppoId,
        String nome,
        Long utenteId,
        String actorEmail,
        Instant occurredAt
) {}
