package it.exprivia.prenotazioni.dto;

public record ExternalStanzaResponse(
        Long id,
        String nome,
        String tipo,
        String layoutElementId,
        Long pianoId,
        Integer pianoNumero
) {
}
