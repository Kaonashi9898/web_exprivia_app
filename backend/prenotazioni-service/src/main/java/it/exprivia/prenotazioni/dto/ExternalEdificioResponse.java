package it.exprivia.prenotazioni.dto;

public record ExternalEdificioResponse(
        Long id,
        String nome,
        Long sedeId,
        String sedeNome
) {
}
