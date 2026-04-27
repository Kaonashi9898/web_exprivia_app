package it.exprivia.location.dto;

import it.exprivia.location.entity.StatoPostazione;
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
    private String layoutElementId;
    private StatoPostazione stato;
    private BigDecimal xPct;
    private BigDecimal yPct;
    private Long stanzaId;
    private String stanzaNome;
}
