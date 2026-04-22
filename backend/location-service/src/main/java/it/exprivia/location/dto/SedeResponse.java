package it.exprivia.location.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SedeResponse {

    private Long id;
    private String nome;
    private String indirizzo;
    private String citta;
    private BigDecimal latitudine;
    private BigDecimal longitudine;
}
