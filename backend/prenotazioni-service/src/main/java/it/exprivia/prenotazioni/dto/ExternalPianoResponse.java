package it.exprivia.prenotazioni.dto;

public record ExternalPianoResponse(
        Long id,
        Integer numero,
        String nome,
        Long edificioId,
        String edificioNome
) {
}
