package it.exprivia.location.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StanzaResponse {

    private Long id;
    private String nome;
    private Long pianoId;
    private Integer pianoNumero;
}
