package it.exprivia.utenti.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Filtro JWT: viene eseguito una volta per ogni richiesta HTTP in arrivo.
 *
 * Funzionamento:
 * 1. Legge l'header "Authorization" dalla richiesta
 * 2. Se contiene un token "Bearer ...", lo estrae e lo valida
 * 3. Se valido, imposta l'autenticazione nel SecurityContext
 *    (Spring Security da quel punto sa chi è l'utente)
 * 4. Passa la richiesta al controller successivo
 *
 * NOTA: non è @Component — viene registrato solo dentro Spring Security
 *       (evita la doppia registrazione come servlet filter)
 */
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_COOKIE_NAME = "EXPRIVIA_AUTH_TOKEN";

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null) {
            if (jwtUtils.isValid(token)) {
                String email = jwtUtils.extractEmail(token);
                String ruolo = jwtUtils.extractRuolo(token);

                // Comunica a Spring Security che l'utente è autenticato, con il suo ruolo
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(email, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + ruolo)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // Passa sempre la richiesta al passo successivo
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> AUTH_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
