package it.exprivia.utenti.controller;

import it.exprivia.utenti.dto.LoginRequest;
import it.exprivia.utenti.dto.LoginResponse;
import it.exprivia.utenti.dto.RegisterRequest;
import it.exprivia.utenti.dto.UtenteDTO;
import it.exprivia.utenti.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String AUTH_COOKIE_NAME = "EXPRIVIA_AUTH_TOKEN";

    private final AuthService authService;

    @Value("${auth.cookie.secure:false}")
    private boolean authCookieSecure;

    @Value("${auth.cookie.same-site:Lax}")
    private String authCookieSameSite;

    @PostMapping("/register")
    public ResponseEntity<UtenteDTO> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildAuthCookie(response.getToken(), response.getExpiresInMillis()).toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, buildAuthCookie("", 0).toString())
                .build();
    }

    private ResponseCookie buildAuthCookie(String value, long maxAgeMillis) {
        return ResponseCookie.from(AUTH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite(authCookieSameSite)
                .path("/")
                .maxAge(Duration.ofMillis(maxAgeMillis))
                .build();
    }
}
