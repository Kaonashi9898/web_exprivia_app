package it.exprivia.location.messaging;

import it.exprivia.location.entity.StatoPostazione;

public record PostazioneNonPrenotabileEvent(
        Long postazioneId,
        String postazioneCodice,
        StatoPostazione stato
) {}
