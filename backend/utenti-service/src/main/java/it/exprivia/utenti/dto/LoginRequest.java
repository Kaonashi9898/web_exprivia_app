package it.exprivia.utenti.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * DTO (Data Transfer Object) per la richiesta di login.
 *
 * Contiene le credenziali inviate dal client nel body della richiesta
 * POST /api/auth/login. Entrambi i campi sono obbligatori (@NotBlank).
 */
@Data
public class LoginRequest {
    @NotBlank(message = "L'email e' obbligatoria")
    @Email(message = "L'email deve essere valida")
    @Pattern(
            regexp = "(?i)^[a-z0-9]+(?:[._-][a-z0-9]+)*@exprivia\\.com$",
            message = "Dominio non autorizzato. Utilizzare esclusivamente un indirizzo @exprivia.com"
    )
    private String email;

    @NotBlank(message = "La password e' obbligatoria")
    private String password;
}
