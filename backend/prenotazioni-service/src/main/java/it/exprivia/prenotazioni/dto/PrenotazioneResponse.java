package it.exprivia.prenotazioni.dto;

import it.exprivia.prenotazioni.entity.StatoPrenotazione;
import it.exprivia.prenotazioni.entity.TipoRisorsaPrenotata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrenotazioneResponse {

    private Long id;
    private Long utenteId;
    private String utenteEmail;
    private String utenteFullName;
    private TipoRisorsaPrenotata tipoRisorsaPrenotata;
    private String risorsaLabel;
    private Long postazioneId;
    private String postazioneCodice;
    private Long meetingRoomStanzaId;
    private String meetingRoomNome;
    private Long stanzaId;
    private String stanzaNome;
    private LocalDate dataPrenotazione;
    private LocalTime oraInizio;
    private LocalTime oraFine;
    private StatoPrenotazione stato;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
