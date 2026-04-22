package it.exprivia.location.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PianoResponse {

    private Long id;
    private Integer numero;
    private String nome;
    private Long edificioId;
    private String edificioNome;
}
