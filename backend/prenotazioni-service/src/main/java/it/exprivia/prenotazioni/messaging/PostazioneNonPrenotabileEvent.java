package it.exprivia.prenotazioni.messaging;

public record PostazioneNonPrenotabileEvent(
        Long postazioneId,
        String postazioneCodice,
        String stato
) {}
