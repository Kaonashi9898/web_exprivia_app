package it.exprivia.prenotazioni.dto;

import java.math.BigDecimal;

public record ExternalPostazioneResponse(
        Long id,
        String codice,
        String layoutElementId,
        String stato,
        BigDecimal xPct,
        BigDecimal yPct,
        Long stanzaId,
        String stanzaNome
) {}
