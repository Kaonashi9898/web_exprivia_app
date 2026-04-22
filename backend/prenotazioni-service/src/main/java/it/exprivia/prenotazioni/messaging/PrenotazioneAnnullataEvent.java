package it.exprivia.prenotazioni.messaging;

import java.time.LocalDate;
import java.time.LocalTime;

public record PrenotazioneAnnullataEvent(
        Long prenotazioneId,
        Long utenteId,
        String utenteEmail,
        String utenteFullName,
        Long postazioneId,
        String postazioneCodice,
        String stanzaNome,
        LocalDate dataPrenotazione,
        LocalTime oraInizio,
        LocalTime oraFine
) {}
