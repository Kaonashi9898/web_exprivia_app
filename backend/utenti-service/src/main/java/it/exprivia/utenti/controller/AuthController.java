package it.exprivia.utenti.controller;

import it.exprivia.utenti.dto.LoginRequest;
import it.exprivia.utenti.dto.LoginResponse;
import it.exprivia.utenti.dto.RegisterRequest;
import it.exprivia.utenti.dto.UtenteDTO;
import it.exprivia.utenti.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller per l'autenticazione degli utenti.
 *
 * Espone gli endpoint pubblici (senza token JWT) per:
 * - POST /api/auth/register → registrazione di un nuovo utente
 * - POST /api/auth/login    → login con email e password, restituisce un token JWT
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Registra un nuovo utente nel sistema.
     * Il body deve essere un JSON valido secondo le regole di {@link RegisterRequest}.
     *
     * @param request dati di registrazione (nome, email, password, ruolo opzionale)
     * @return il DTO dell'utente appena creato
     */
    @PostMapping("/register")
    public ResponseEntity<UtenteDTO> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * Effettua il login e restituisce un token JWT.
     * Il token dovrà essere incluso nelle successive richieste nell'header:
     * {@code Authorization: Bearer <token>}
     *
     * @param request credenziali di accesso (email e password)
     * @return un oggetto contenente il token JWT
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
