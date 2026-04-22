package it.exprivia.utenti.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO (Data Transfer Object) per la richiesta di login.
 *
 * Contiene le credenziali inviate dal client nel body della richiesta
 * POST /api/auth/login. Entrambi i campi sono obbligatori (@NotBlank).
 */
@Data
public class LoginRequest {
    @NotBlank
    private String email;
    @NotBlank
    private String password;
}
