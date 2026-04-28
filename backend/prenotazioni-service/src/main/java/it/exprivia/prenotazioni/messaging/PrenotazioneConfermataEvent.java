package it.exprivia.prenotazioni.messaging;

import it.exprivia.prenotazioni.entity.TipoRisorsaPrenotata;

import java.time.LocalDate;
import java.time.LocalTime;

public record PrenotazioneConfermataEvent(
        Long prenotazioneId,
        Long utenteId,
        String utenteEmail,
        String utenteFullName,
        TipoRisorsaPrenotata tipoRisorsaPrenotata,
        String risorsaLabel,
        Long postazioneId,
        String postazioneCodice,
        Long meetingRoomStanzaId,
        String meetingRoomNome,
        Long stanzaId,
        String stanzaNome,
        LocalDate dataPrenotazione,
        LocalTime oraInizio,
        LocalTime oraFine
) {}
