package it.exprivia.location.dto;

import it.exprivia.location.entity.StatoPostazione;
import it.exprivia.location.entity.TipoPostazione;
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

    private String cadId;

    @NotNull
    private TipoPostazione tipo;

    private StatoPostazione stato = StatoPostazione.DISPONIBILE;

    private Boolean accessibile = Boolean.FALSE;

    private BigDecimal x;
    private BigDecimal y;

    @NotNull
    private Long stanzaId;
}
