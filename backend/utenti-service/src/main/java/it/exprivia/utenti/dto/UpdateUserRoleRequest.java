package it.exprivia.utenti.dto;

import it.exprivia.utenti.entity.RuoloUtente;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {

    @NotNull(message = "Il ruolo e' obbligatorio")
    private RuoloUtente ruolo;
}
