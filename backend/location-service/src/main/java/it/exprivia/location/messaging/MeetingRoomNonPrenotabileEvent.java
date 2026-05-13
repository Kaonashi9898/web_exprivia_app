package it.exprivia.location.messaging;

import it.exprivia.location.entity.StatoPostazione;

public record MeetingRoomNonPrenotabileEvent(
        Long stanzaId,
        String stanzaNome,
        StatoPostazione stato
) {}
