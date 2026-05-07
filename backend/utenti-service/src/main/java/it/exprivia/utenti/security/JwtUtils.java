package it.exprivia.utenti.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Classe di utilità per la gestione dei token JWT.
 *
 * Un token JWT è composto da 3 parti separate da un punto:
 *   HEADER.PAYLOAD.FIRMA
 *
 * - HEADER: tipo di algoritmo usato (es. HS256)
 * - PAYLOAD: i dati (es. email, ruolo, scadenza)
 * - FIRMA: la prova che il token non è stato manomesso
 */
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // Costruisce la chiave crittografica a partire dalla stringa segreta
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Genera un nuovo token JWT per l'utente.
     * Il token contiene: email (subject), ruolo, data di emissione, data di scadenza.
     */
    public String generateToken(String email, String ruolo) {
        return Jwts.builder()
                .subject(email)
                .claim("ruolo", ruolo)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    public long getExpirationMillis() {
        return expiration;
    }

    /**
     * Estrae l'email (subject) dal token.
     */
    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Estrae il ruolo dal token.
     */
    public String extractRuolo(String token) {
        return getClaims(token).get("ruolo", String.class);
    }

    /**
     * Verifica che il token sia valido (firma corretta e non scaduto).
     */
    public boolean isValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
