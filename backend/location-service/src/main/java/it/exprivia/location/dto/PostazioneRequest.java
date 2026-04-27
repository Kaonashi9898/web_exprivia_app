package it.exprivia.location.dto;

import it.exprivia.location.entity.StatoPostazione;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostazioneRequest {

    @NotBlank
    private String codice;

    private String layoutElementId;

    private StatoPostazione stato = StatoPostazione.DISPONIBILE;

    private BigDecimal xPct;
    private BigDecimal yPct;

    @NotNull
    private Long stanzaId;
}
