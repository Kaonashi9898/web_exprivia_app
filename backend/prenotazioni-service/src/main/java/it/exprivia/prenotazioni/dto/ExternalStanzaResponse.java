package it.exprivia.prenotazioni.dto;

public record ExternalStanzaResponse(
        Long id,
        String nome,
        Long pianoId,
        Integer pianoNumero
) {
}
