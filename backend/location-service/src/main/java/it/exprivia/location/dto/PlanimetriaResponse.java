package it.exprivia.location.dto;

import it.exprivia.location.entity.FormatoFile;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanimetriaResponse {

    private Long id;
    private Long pianoId;
    private String imageName;
    private FormatoFile formatoOriginale;
    private BigDecimal coordXmin;
    private BigDecimal coordXmax;
    private BigDecimal coordYmin;
    private BigDecimal coordYmax;
    private String imageUrl;
    private String postazioniUrl;
    private String layoutUrl;
}
