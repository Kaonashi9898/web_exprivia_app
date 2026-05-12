package it.exprivia.utenti.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordResetRequestCreateRequest {

    @NotBlank(message = "L'email e' obbligatoria")
    @Email(message = "L'email deve avere un formato valido")
    private String email;
}
