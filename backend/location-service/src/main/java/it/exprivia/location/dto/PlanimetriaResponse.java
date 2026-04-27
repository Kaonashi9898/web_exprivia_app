package it.exprivia.location.dto;

import it.exprivia.location.entity.FormatoFile;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanimetriaResponse {

    private Long id;
    private Long pianoId;
    private String imageName;
    private FormatoFile formatoOriginale;
    private String imageUrl;
    private String postazioniUrl;
    private String layoutUrl;
}
