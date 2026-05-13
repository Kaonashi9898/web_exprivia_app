package it.exprivia.prenotazioni.messaging;

public record MeetingRoomNonPrenotabileEvent(
        Long stanzaId,
        String stanzaNome,
        String stato
) {}
