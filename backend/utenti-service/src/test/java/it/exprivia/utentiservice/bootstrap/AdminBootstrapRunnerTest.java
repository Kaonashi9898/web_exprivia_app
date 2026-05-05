package it.exprivia.utentiservice.bootstrap;

import it.exprivia.utenti.bootstrap.AdminBootstrapProperties;
import it.exprivia.utenti.bootstrap.AdminBootstrapRunner;
import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
import it.exprivia.utenti.repository.UtenteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock UtenteRepository utenteRepository;
    @Mock PasswordEncoder passwordEncoder;

    private final AdminBootstrapProperties properties = new AdminBootstrapProperties();

    @Test
    void run_disabilitato_nonFaNulla() throws Exception {
        properties.setEnabled(false);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, utenteRepository, passwordEncoder);
        runner.run(null);

        verify(utenteRepository, never()).save(any(Utente.class));
    }

    @Test
    void run_adminGiaEsistente_nonCreaDuplicati() throws Exception {
        properties.setEnabled(true);
        when(utenteRepository.existsByRuolo(RuoloUtente.ADMIN)).thenReturn(true);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, utenteRepository, passwordEncoder);
        runner.run(null);

        verify(utenteRepository, never()).save(any(Utente.class));
    }

    @Test
    void run_configurazioneValida_creaAdmin() throws Exception {
        properties.setEnabled(true);
        properties.setFullName("System Admin");
        properties.setEmail("admin.bootstrap@exprivia.com");
        properties.setPassword("SecurePassword123!");
        when(utenteRepository.existsByRuolo(RuoloUtente.ADMIN)).thenReturn(false);
        when(utenteRepository.findByEmail(properties.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(properties.getPassword())).thenReturn("hashed");

        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, utenteRepository, passwordEncoder);
        runner.run(null);

        ArgumentCaptor<Utente> captor = ArgumentCaptor.forClass(Utente.class);
        verify(utenteRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("System Admin");
        assertThat(captor.getValue().getEmail()).isEqualTo("admin.bootstrap@exprivia.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(captor.getValue().getRuolo()).isEqualTo(RuoloUtente.ADMIN);
    }

    @Test
    void run_emailBootstrapEsistenteNonAdmin_promuoveAdAdmin() throws Exception {
        properties.setEnabled(true);
        properties.setFullName("System Admin");
        properties.setEmail("admin.bootstrap@exprivia.com");
        properties.setPassword("SecurePassword123!");
        Utente existing = new Utente();
        existing.setId(10L);
        existing.setFullName("Pending Admin");
        existing.setEmail("admin.bootstrap@exprivia.com");
        existing.setPasswordHash("old-hash");
        existing.setRuolo(RuoloUtente.GUEST);

        when(utenteRepository.existsByRuolo(RuoloUtente.ADMIN)).thenReturn(false);
        when(utenteRepository.findByEmail(properties.getEmail())).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(properties.getPassword())).thenReturn("hashed");

        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, utenteRepository, passwordEncoder);
        runner.run(null);

        ArgumentCaptor<Utente> captor = ArgumentCaptor.forClass(Utente.class);
        verify(utenteRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(10L);
        assertThat(captor.getValue().getFullName()).isEqualTo("System Admin");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(captor.getValue().getRuolo()).isEqualTo(RuoloUtente.ADMIN);
    }

    @Test
    void run_enabledSenzaValoriRichiesti_lanciaErrore() {
        properties.setEnabled(true);
        when(utenteRepository.existsByRuolo(RuoloUtente.ADMIN)).thenReturn(false);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, utenteRepository, passwordEncoder);
        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_FULL_NAME");
    }
}
