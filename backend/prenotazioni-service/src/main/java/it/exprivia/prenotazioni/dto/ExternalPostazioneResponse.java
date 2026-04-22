package it.exprivia.prenotazioni.dto;

import java.math.BigDecimal;

public record ExternalPostazioneResponse(
        Long id,
        String codice,
        String cadId,
        String tipo,
        String stato,
        Boolean accessibile,
        BigDecimal x,
        BigDecimal y,
        Long stanzaId,
        String stanzaNome
) {}
