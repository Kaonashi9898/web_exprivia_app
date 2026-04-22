package it.exprivia.prenotazioni.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;



@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePrenotazioneRequest {

    @NotNull
    private Long postazioneId;

    @NotNull
    @FutureOrPresent
    private LocalDate dataPrenotazione;

    @NotNull
    private LocalTime oraInizio;

    @NotNull
    private LocalTime oraFine;
}
