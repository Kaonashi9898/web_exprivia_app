package it.exprivia.utenti.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGruppoRequest(
        @NotBlank(message = "Il nome del gruppo e' obbligatorio")
        @Size(max = 50, message = "Il nome del gruppo non puo' superare i 50 caratteri")
        String nome
) {
}
