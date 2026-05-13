package it.exprivia.prenotazioni.dto;

import it.exprivia.prenotazioni.entity.MotivoNotificaPrenotazione;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrenotazioneNotificaResponse {

    private Long id;
    private MotivoNotificaPrenotazione motivo;
    private String risorsaLabel;
    private String stanzaNome;
    private LocalDate dataPrenotazione;
    private LocalTime oraInizio;
    private LocalTime oraFine;
    private String statoPostazione;
    private OffsetDateTime createdAt;
}
