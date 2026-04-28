package it.exprivia.utenti.dto;

import it.exprivia.utenti.entity.RuoloUtente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO per la richiesta di registrazione di un nuovo utente.
 *
 * Contiene le informazioni inviate nel body di POST /api/auth/register.
 * I vincoli di validazione (@NotBlank, @Size, @Pattern) vengono
 * verificati automaticamente da Spring prima di entrare nel controller.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Il nome completo è obbligatorio")
    @Size(max = 50, message = "Il nome non può superare i 50 caratteri")
    private String fullName;

    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "L'email deve essere valida")
    @Pattern(
        regexp = "(?i)^[a-z0-9]+(?:[._-][a-z0-9]+)*@exprivia\\.com$",
        message = "L'email deve essere un indirizzo aziendale @exprivia.com valido"
    )
    private String email;

    @NotBlank(message = "La password è obbligatoria")
    @Size(min = 8, message = "La password deve essere di almeno 8 caratteri")
    private String password;

    private RuoloUtente ruolo; // opzionale: se non specificato usa USER
}
