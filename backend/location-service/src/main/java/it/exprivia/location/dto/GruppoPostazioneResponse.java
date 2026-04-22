package it.exprivia.location.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GruppoPostazioneResponse {

    private Long id;
    private Long gruppoId;
    private Long postazioneId;
    private String postazioneCodice;
}
