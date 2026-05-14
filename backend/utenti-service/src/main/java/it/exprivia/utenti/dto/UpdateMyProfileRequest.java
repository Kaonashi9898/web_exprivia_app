package it.exprivia.utenti.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateMyProfileRequest {

    @NotBlank(message = "Il nome completo e' obbligatorio")
    @Size(max = 50, message = "Il nome non puo' superare i 50 caratteri")
    private String fullName;
}
