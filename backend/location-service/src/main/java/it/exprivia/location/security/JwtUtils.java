package it.exprivia.location.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Classe di utilità per la lettura e validazione dei token JWT.
 *
 * In questo microservizio i token vengono SOLO letti e validati,
 * non generati (la generazione avviene solo in utenti-service).
 * La chiave segreta (jwt.secret) deve essere identica a quella di utenti-service.
 */
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    // Costruisce la chiave crittografica dalla stringa segreta
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Estrae l'email dell'utente (subject) dal token JWT. */
    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    /** Estrae il ruolo dell'utente dal payload del token JWT. */
    public String extractRuolo(String token) {
        return getClaims(token).get("ruolo", String.class);
    }

    /**
     * Verifica che il token sia valido: firma corretta e non scaduto.
     * Restituisce false in caso di qualsiasi eccezione JWT.
     */
    public boolean isValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    // Parsa il token e restituisce il payload (Claims) verificando la firma
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
