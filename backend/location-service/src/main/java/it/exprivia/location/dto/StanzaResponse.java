package it.exprivia.location.dto;

import it.exprivia.location.entity.TipoStanza;
import it.exprivia.location.entity.StatoPostazione;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StanzaResponse {

    private Long id;
    private String nome;
    private TipoStanza tipo;
    private StatoPostazione stato;
    private String layoutElementId;
    private BigDecimal xPct;
    private BigDecimal yPct;
    private Long pianoId;
    private Integer pianoNumero;
}
