package it.exprivia.location.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro JWT per il microservizio location.
 *
 * Funziona esattamente come quello di utenti-service:
 * viene eseguito una sola volta per ogni richiesta HTTP (OncePerRequestFilter),
 * legge il token dall'header Authorization, lo valida e imposta
 * l'autenticazione nel SecurityContext di Spring Security.
 *
 * Nota: questo servizio NON genera token JWT, li legge e li verifica soltanto.
 * La chiave segreta JWT deve essere la stessa usata da utenti-service.
 */
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Controlla che l'header sia presente e nel formato "Bearer <token>"
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // rimuove il prefisso "Bearer "

            if (jwtUtils.isValid(token)) {
                String email = jwtUtils.extractEmail(token);
                String ruolo = jwtUtils.extractRuolo(token);

                // Comunica a Spring Security che l'utente è autenticato con il suo ruolo
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(email, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + ruolo)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // Passa sempre la richiesta al passo successivo della catena
        filterChain.doFilter(request, response);
    }
}
