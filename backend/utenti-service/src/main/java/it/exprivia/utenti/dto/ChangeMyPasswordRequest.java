package it.exprivia.utenti.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeMyPasswordRequest {

    @NotBlank(message = "La password attuale e' obbligatoria")
    private String currentPassword;

    @NotBlank(message = "La nuova password e' obbligatoria")
    @Size(min = 8, message = "La nuova password deve essere di almeno 8 caratteri")
    private String newPassword;
}
