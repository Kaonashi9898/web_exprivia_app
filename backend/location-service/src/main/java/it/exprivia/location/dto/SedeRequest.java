package it.exprivia.location.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SedeRequest {

    @NotBlank
    private String nome;

    @NotBlank
    private String indirizzo;

    @NotBlank
    private String citta;

    private BigDecimal latitudine;
    private BigDecimal longitudine;
}
