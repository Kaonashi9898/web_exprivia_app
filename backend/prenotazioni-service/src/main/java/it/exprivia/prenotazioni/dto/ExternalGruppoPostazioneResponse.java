package it.exprivia.prenotazioni.dto;

public record ExternalGruppoPostazioneResponse(
        Long id,
        Long gruppoId,
        Long postazioneId,
        String postazioneCodice
) {}
