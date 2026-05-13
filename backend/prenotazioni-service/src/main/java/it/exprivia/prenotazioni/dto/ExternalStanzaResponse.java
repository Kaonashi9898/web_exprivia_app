package it.exprivia.prenotazioni.dto;

public record ExternalStanzaResponse(
        Long id,
        String nome,
        String tipo,
        String stato,
        String layoutElementId,
        Long pianoId,
        Integer pianoNumero
) {
    public ExternalStanzaResponse(
            Long id,
            String nome,
            String tipo,
            String layoutElementId,
            Long pianoId,
            Integer pianoNumero
    ) {
        this(id, nome, tipo, "DISPONIBILE", layoutElementId, pianoId, pianoNumero);
    }
}
