package it.exprivia.utenti.service;

import it.exprivia.utenti.dto.LoginRequest;
import it.exprivia.utenti.dto.LoginResponse;
import it.exprivia.utenti.dto.RegisterRequest;
import it.exprivia.utenti.dto.UtenteDTO;
import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
import it.exprivia.utenti.messaging.GruppoEventPublisher;
import it.exprivia.utenti.repository.UtenteRepository;
import it.exprivia.utenti.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtenteRepository utenteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final GruppoEventPublisher gruppoEventPublisher;

    public UtenteDTO register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (utenteRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email gia' registrata");
        }

        Utente utente = new Utente();
        utente.setFullName(request.getFullName());
        utente.setEmail(normalizedEmail);
        utente.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        utente.setRuolo(resolvePublicRegistrationRole(request.getRuolo()));

        Utente saved = utenteRepository.save(utente);
        UtenteDTO dto = new UtenteDTO(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRuolo());
        gruppoEventPublisher.pubblicaCreazioneUtente(dto, normalizedEmail);
        return dto;
    }

    public LoginResponse login(LoginRequest request) {
        Utente utente = utenteRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Credenziali non valide. Controlla email e password e riprova."));

        if (!passwordEncoder.matches(request.getPassword(), utente.getPasswordHash())) {
            throw new IllegalArgumentException("Credenziali non valide. Controlla email e password e riprova.");
        }

        String token = jwtUtils.generateToken(utente.getEmail(), utente.getRuolo().name());
        UtenteDTO user = new UtenteDTO(utente.getId(), utente.getFullName(), utente.getEmail(), utente.getRuolo());
        return new LoginResponse(token, user, jwtUtils.getExpirationMillis());
    }

    private RuoloUtente resolvePublicRegistrationRole(RuoloUtente requestedRole) {
        return RuoloUtente.GUEST;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
