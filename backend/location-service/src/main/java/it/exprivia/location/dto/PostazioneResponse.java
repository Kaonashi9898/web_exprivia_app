package it.exprivia.location.dto;

import it.exprivia.location.entity.StatoPostazione;
import it.exprivia.location.entity.TipoPostazione;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostazioneResponse {

    private Long id;
    private String codice;
    private String cadId;
    private TipoPostazione tipo;
    private StatoPostazione stato;
    private Boolean accessibile;
    private BigDecimal x;
    private BigDecimal y;
    private Long stanzaId;
    private String stanzaNome;
}
