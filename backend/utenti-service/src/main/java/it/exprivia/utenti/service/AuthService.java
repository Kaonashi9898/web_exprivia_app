package it.exprivia.utenti.service;

import it.exprivia.utenti.dto.LoginRequest;
import it.exprivia.utenti.dto.LoginResponse;
import it.exprivia.utenti.dto.RegisterRequest;
import it.exprivia.utenti.dto.UtenteDTO;
import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
import it.exprivia.utenti.repository.UtenteRepository;
import it.exprivia.utenti.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtenteRepository utenteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public UtenteDTO register(RegisterRequest request) {
        if (utenteRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email gia' registrata");
        }

        Utente utente = new Utente();
        utente.setFullName(request.getFullName());
        utente.setEmail(request.getEmail());
        utente.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        utente.setRuolo(resolvePublicRegistrationRole(request.getRuolo()));

        Utente saved = utenteRepository.save(utente);
        return new UtenteDTO(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRuolo());
    }

    public LoginResponse login(LoginRequest request) {
        Utente utente = utenteRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credenziali non valide"));

        if (!passwordEncoder.matches(request.getPassword(), utente.getPasswordHash())) {
            throw new IllegalArgumentException("Credenziali non valide");
        }

        String token = jwtUtils.generateToken(utente.getEmail(), utente.getRuolo().name());
        return new LoginResponse(token);
    }

    private RuoloUtente resolvePublicRegistrationRole(RuoloUtente requestedRole) {
        if (requestedRole == null) {
            return RuoloUtente.USER;
        }
        if (requestedRole == RuoloUtente.USER || requestedRole == RuoloUtente.GUEST) {
            return requestedRole;
        }
        throw new IllegalArgumentException("La registrazione pubblica puo' creare solo utenti USER o GUEST");
    }
}
