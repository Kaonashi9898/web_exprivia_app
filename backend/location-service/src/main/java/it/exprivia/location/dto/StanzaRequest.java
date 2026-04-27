package it.exprivia.location.dto;

import it.exprivia.location.entity.TipoStanza;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StanzaRequest {

    @NotBlank
    private String nome;

    @NotNull
    private TipoStanza tipo;

    private String layoutElementId;

    private BigDecimal xPct;
    private BigDecimal yPct;

    @NotNull
    private Long pianoId;
}
