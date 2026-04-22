package it.exprivia.utenti.bootstrap;

import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
import it.exprivia.utenti.repository.UtenteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final UtenteRepository utenteRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        if (utenteRepository.existsByRuolo(RuoloUtente.ADMIN)) {
            log.info("Admin bootstrap skipped because an ADMIN user already exists.");
            return;
        }

        String fullName = requireValue(properties.getFullName(), "ADMIN_BOOTSTRAP_FULL_NAME");
        String email = requireValue(properties.getEmail(), "ADMIN_BOOTSTRAP_EMAIL");
        String password = requireValue(properties.getPassword(), "ADMIN_BOOTSTRAP_PASSWORD");

        if (utenteRepository.existsByEmail(email)) {
            throw new IllegalStateException("Admin bootstrap email already exists but is not assigned to an ADMIN user: " + email);
        }

        Utente admin = new Utente();
        admin.setFullName(fullName);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRuolo(RuoloUtente.ADMIN);
        utenteRepository.save(admin);

        log.info("Bootstrap admin created successfully for {}", email);
    }

    private String requireValue(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required bootstrap setting: " + envName);
        }
        return value.trim();
    }
}
