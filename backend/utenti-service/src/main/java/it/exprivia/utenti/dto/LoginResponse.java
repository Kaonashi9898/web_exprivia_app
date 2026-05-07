package it.exprivia.utenti.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO restituito al client dopo un login riuscito.
 *
 * Il token viene usato dal controller per impostare un cookie HttpOnly
 * e non viene serializzato nel JSON di risposta.
 */
@Data
@AllArgsConstructor
public class LoginResponse {
    @JsonIgnore
    private String token;

    private UtenteDTO user;

    @JsonIgnore
    private long expiresInMillis;
}
