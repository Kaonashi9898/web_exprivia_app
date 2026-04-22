package it.exprivia.location.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EdificioResponse {

    private Long id;
    private String nome;
    private Long sedeId;
    private String sedeNome;
}
