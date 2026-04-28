package it.exprivia.prenotazioni.dto;

public record ExternalSedeResponse(
        Long id,
        String nome,
        String indirizzo,
        String citta
) {
}
