package it.exprivia.prenotazioni.dto;

import it.exprivia.prenotazioni.entity.RuoloUtente;

public record ExternalUtenteResponse(
        Long id,
        String fullName,
        String email,
        RuoloUtente ruolo
) {}
