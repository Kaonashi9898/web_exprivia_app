package it.exprivia.utentiservice.service;

import it.exprivia.utenti.dto.RegisterRequest;
import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
import it.exprivia.utenti.repository.UtenteRepository;
import it.exprivia.utenti.security.JwtUtils;
import it.exprivia.utenti.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UtenteRepository utenteRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;

    @InjectMocks AuthService authService;

    @Test
    void register_senzaRuoloEsplicito_assegnaUser() {
        RegisterRequest request = buildRequest("mario.rossi@exprivia.com", null);
        when(utenteRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hash");
        when(utenteRepository.save(any(Utente.class))).thenAnswer(invocation -> {
            Utente utente = invocation.getArgument(0);
            utente.setId(1L);
            return utente;
        });

        var response = authService.register(request);

        ArgumentCaptor<Utente> captor = ArgumentCaptor.forClass(Utente.class);
        verify(utenteRepository).save(captor.capture());
        assertThat(captor.getValue().getRuolo()).isEqualTo(RuoloUtente.USER);
        assertThat(response.getRuolo()).isEqualTo(RuoloUtente.USER);
    }

    @Test
    void register_guestRestaConsentito() {
        RegisterRequest request = buildRequest("guest.user@exprivia.com", RuoloUtente.GUEST);
        when(utenteRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hash");
        when(utenteRepository.save(any(Utente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(request);

        assertThat(response.getRuolo()).isEqualTo(RuoloUtente.GUEST);
    }

    @Test
    void register_normalizzaEmailPrimaDiSalvarla() {
        RegisterRequest request = buildRequest("  Mario.De-Santis@Exprivia.com  ", null);
        when(utenteRepository.existsByEmail("mario.de-santis@exprivia.com")).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hash");
        when(utenteRepository.save(any(Utente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(request);

        ArgumentCaptor<Utente> captor = ArgumentCaptor.forClass(Utente.class);
        verify(utenteRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("mario.de-santis@exprivia.com");
        assertThat(response.getEmail()).isEqualTo("mario.de-santis@exprivia.com");
    }

    @Test
    void register_ruoloPrivilegiatoVieneRifiutato() {
        RegisterRequest request = buildRequest("admin.user@exprivia.com", RuoloUtente.ADMIN);
        when(utenteRepository.existsByEmail(request.getEmail())).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USER o GUEST");
    }

    private RegisterRequest buildRequest(String email, RuoloUtente ruolo) {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Mario Rossi");
        request.setEmail(email);
        request.setPassword("password123");
        request.setRuolo(ruolo);
        return request;
    }
}
