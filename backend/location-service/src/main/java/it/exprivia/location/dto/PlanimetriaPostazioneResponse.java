package it.exprivia.location.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanimetriaPostazioneResponse {

    private String id;
    private String pdl;
    private String stanza;
    private BigDecimal xPct;
    private BigDecimal yPct;
}
