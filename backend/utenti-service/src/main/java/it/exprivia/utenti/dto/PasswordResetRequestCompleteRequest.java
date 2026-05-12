package it.exprivia.utenti.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetRequestCompleteRequest {

    @NotBlank(message = "La password temporanea e' obbligatoria")
    @Size(min = 8, message = "La password temporanea deve essere di almeno 8 caratteri")
    private String temporaryPassword;
}
