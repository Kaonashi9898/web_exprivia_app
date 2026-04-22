package it.exprivia.location.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GruppoPostazioneRequest {

    @NotNull
    private Long gruppoId;

    @NotNull
    private Long postazioneId;
}
