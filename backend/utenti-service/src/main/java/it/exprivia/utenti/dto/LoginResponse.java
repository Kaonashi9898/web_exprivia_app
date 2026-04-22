package it.exprivia.utenti.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO restituito al client dopo un login riuscito.
 *
 * Contiene il token JWT che il client dovrà includere
 * nell'header di ogni richiesta successiva:
 * {@code Authorization: Bearer <token>}
 */
@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
}
