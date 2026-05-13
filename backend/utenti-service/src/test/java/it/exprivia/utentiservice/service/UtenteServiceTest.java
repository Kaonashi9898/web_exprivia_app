package it.exprivia.utentiservice.service;

import it.exprivia.utenti.dto.ChangeMyPasswordRequest;
import it.exprivia.utenti.dto.RegisterRequest;
import it.exprivia.utenti.dto.UpdateMyProfileRequest;
import it.exprivia.utenti.messaging.EventPublicationException;
import it.exprivia.utenti.messaging.GruppoEventPublisher;
import it.exprivia.utenti.messaging.UtenteEliminatoEvent;
import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
import it.exprivia.utenti.repository.GruppoUtenteRepository;
import it.exprivia.utenti.repository.UtenteRepository;
import it.exprivia.utenti.service.UtenteService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtenteServiceTest {

    @Mock UtenteRepository utenteRepository;
    @Mock GruppoUtenteRepository gruppoUtenteRepository;
    @Mock GruppoEventPublisher gruppoEventPublisher;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UtenteService utenteService;

    @Test
    void updateMyProfile_normalizzaNomeEPubblicaEvento() {
        when(utenteRepository.findByEmail("user@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(15L, "Mario Rossi", "user@exprivia.com", RuoloUtente.USER)));
        when(utenteRepository.save(any(Utente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setFullName("  Mario   Rossi Bianchi  ");

        var response = utenteService.updateMyProfile("user@exprivia.com", request);

        ArgumentCaptor<Utente> captor = ArgumentCaptor.forClass(Utente.class);
        verify(utenteRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("Mario Rossi Bianchi");
        assertThat(response.getFullName()).isEqualTo("Mario Rossi Bianchi");
        verify(gruppoEventPublisher).pubblicaAggiornamentoUtente(response, "user@exprivia.com");
    }

    @Test
    void updateMyProfile_propagaErroreQuandoIlPublishCriticoFallisce() {
        when(utenteRepository.findByEmail("user@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(15L, "Mario Rossi", "user@exprivia.com", RuoloUtente.USER)));
        when(utenteRepository.save(any(Utente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new EventPublicationException("rabbit failure", new RuntimeException("down")))
                .when(gruppoEventPublisher).pubblicaAggiornamentoUtente(any(), any());

        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setFullName("Mario Rossi Bianchi");

        assertThatThrownBy(() -> utenteService.updateMyProfile("user@exprivia.com", request))
                .isInstanceOf(EventPublicationException.class)
                .hasMessageContaining("rabbit failure");
    }

    @Test
    void changeMyPassword_rifiutaPasswordAttualeErrata() {
        when(utenteRepository.findByEmail("user@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(15L, "Mario Rossi", "user@exprivia.com", RuoloUtente.USER)));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        ChangeMyPasswordRequest request = new ChangeMyPasswordRequest();
        request.setCurrentPassword("wrong-password");
        request.setNewPassword("nuovaPassword123");

        assertThatThrownBy(() -> utenteService.changeMyPassword("user@exprivia.com", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("password attuale");

        verify(utenteRepository, never()).save(any(Utente.class));
    }

    @Test
    void changeMyPassword_aggiornaHashQuandoLaPasswordAttualeECorretta() {
        when(utenteRepository.findByEmail("user@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(15L, "Mario Rossi", "user@exprivia.com", RuoloUtente.USER)));
        when(passwordEncoder.matches("passwordAttuale123", "hash")).thenReturn(true);
        when(passwordEncoder.matches("nuovaPassword123", "hash")).thenReturn(false);
        when(passwordEncoder.encode("nuovaPassword123")).thenReturn("new-hash");
        when(utenteRepository.save(any(Utente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChangeMyPasswordRequest request = new ChangeMyPasswordRequest();
        request.setCurrentPassword("passwordAttuale123");
        request.setNewPassword("nuovaPassword123");

        utenteService.changeMyPassword("user@exprivia.com", request);

        ArgumentCaptor<Utente> captor = ArgumentCaptor.forClass(Utente.class);
        verify(utenteRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void create_normalizzaEmailPrimaDiSalvarla() {
        when(utenteRepository.findByEmail("admin@exprivia.com")).thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin@exprivia.com", RuoloUtente.ADMIN)));
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Mario Rossi");
        request.setEmail("  Mario.De-Santis@Exprivia.com  ");
        request.setPassword("password123");
        request.setRuolo(RuoloUtente.USER);

        when(utenteRepository.existsByEmail("mario.de-santis@exprivia.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(utenteRepository.save(any(Utente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = utenteService.create(request, "admin@exprivia.com");

        ArgumentCaptor<Utente> captor = ArgumentCaptor.forClass(Utente.class);
        verify(utenteRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("mario.de-santis@exprivia.com");
        assertThat(response.getEmail()).isEqualTo("mario.de-santis@exprivia.com");
    }

    @Test
    void create_receptionPuoCreareSoloUserOGuest() {
        when(utenteRepository.findByEmail("reception@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(101L, "Reception", "reception@exprivia.com", RuoloUtente.RECEPTION)));
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Manager");
        request.setEmail("manager@exprivia.com");
        request.setPassword("password123");
        request.setRuolo(RuoloUtente.BUILDING_MANAGER);

        assertThatThrownBy(() -> utenteService.create(request, "reception@exprivia.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("USER o GUEST");

        verify(utenteRepository, never()).save(any(Utente.class));
    }

    @Test
    void create_propagaErroreQuandoIlPublishCriticoFallisce() {
        when(utenteRepository.findByEmail("admin@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin@exprivia.com", RuoloUtente.ADMIN)));
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Mario Rossi");
        request.setEmail("mario.rossi@exprivia.com");
        request.setPassword("password123");
        request.setRuolo(RuoloUtente.USER);

        when(utenteRepository.existsByEmail("mario.rossi@exprivia.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(utenteRepository.save(any(Utente.class))).thenAnswer(invocation -> {
            Utente utente = invocation.getArgument(0);
            utente.setId(55L);
            return utente;
        });
        doThrow(new EventPublicationException("rabbit failure", new RuntimeException("down")))
                .when(gruppoEventPublisher).pubblicaCreazioneUtente(any(), any());

        assertThatThrownBy(() -> utenteService.create(request, "admin@exprivia.com"))
                .isInstanceOf(EventPublicationException.class)
                .hasMessageContaining("rabbit failure");
    }

    @Test
    void updateRole_receptionNonPuoModificareAdminReceptionOBuildingManager() {
        when(utenteRepository.findByEmail("reception@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(101L, "Reception", "reception@exprivia.com", RuoloUtente.RECEPTION)));
        when(utenteRepository.findById(5L))
                .thenReturn(java.util.Optional.of(buildUser(5L, "Boss", "boss@exprivia.com", RuoloUtente.ADMIN)));

        assertThatThrownBy(() -> utenteService.updateRole(5L, RuoloUtente.USER, "reception@exprivia.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("USER o GUEST");
    }

    @Test
    void updateRole_receptionNonPuoPromuovereUserARuoliElevati() {
        when(utenteRepository.findByEmail("reception@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(101L, "Reception", "reception@exprivia.com", RuoloUtente.RECEPTION)));
        when(utenteRepository.findById(7L))
                .thenReturn(java.util.Optional.of(buildUser(7L, "Guest User", "guest@exprivia.com", RuoloUtente.GUEST)));

        assertThatThrownBy(() -> utenteService.updateRole(7L, RuoloUtente.ADMIN, "reception@exprivia.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("USER o GUEST");
    }

    @Test
    void updateRole_adminNonPuoModificareIlProprioRuolo() {
        when(utenteRepository.findByEmail("admin.bootstrap@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin.bootstrap@exprivia.com", RuoloUtente.ADMIN)));
        when(utenteRepository.findById(100L))
                .thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin.bootstrap@exprivia.com", RuoloUtente.ADMIN)));

        assertThatThrownBy(() -> utenteService.updateRole(100L, RuoloUtente.USER, "admin.bootstrap@exprivia.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("tuo ruolo");

        verify(utenteRepository, never()).save(any(Utente.class));
    }

    @Test
    void delete_receptionPuoEliminareSoloUserOGuest() {
        when(utenteRepository.findByEmail("reception@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(101L, "Reception", "reception@exprivia.com", RuoloUtente.RECEPTION)));
        when(utenteRepository.findById(9L))
                .thenReturn(java.util.Optional.of(buildUser(9L, "Building", "building@exprivia.com", RuoloUtente.BUILDING_MANAGER)));

        assertThatThrownBy(() -> utenteService.delete(9L, "reception@exprivia.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("USER o GUEST");

        verify(gruppoUtenteRepository, never()).deleteByIdUtente(any());
    }

    @Test
    void delete_adminPuoEliminareUnAltroAdmin() {
        when(utenteRepository.findByEmail("admin@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin@exprivia.com", RuoloUtente.ADMIN)));
        when(utenteRepository.findById(101L))
                .thenReturn(java.util.Optional.of(buildUser(101L, "Altro Admin", "altro.admin@exprivia.com", RuoloUtente.ADMIN)));

        utenteService.delete(101L, "admin@exprivia.com");

        verify(gruppoUtenteRepository).deleteByIdUtente(101L);
        verify(utenteRepository).deleteById(101L);
    }

    @Test
    void delete_adminNonPuoAutoEliminarsi() {
        when(utenteRepository.findByEmail("admin.bootstrap@exprivia.com"))
                .thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin.bootstrap@exprivia.com", RuoloUtente.ADMIN)));
        when(utenteRepository.findById(100L))
                .thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin.bootstrap@exprivia.com", RuoloUtente.ADMIN)));

        assertThatThrownBy(() -> utenteService.delete(100L, "admin.bootstrap@exprivia.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("tuo account");

        verify(gruppoUtenteRepository, never()).deleteByIdUtente(any());
        verify(utenteRepository, never()).deleteById(any());
    }

    @Test
    void delete_utenteInesistente_lanceEntityNotFoundException() {
        when(utenteRepository.findByEmail("admin@exprivia.com")).thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin@exprivia.com", RuoloUtente.ADMIN)));
        when(utenteRepository.findById(1L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> utenteService.delete(1L, "admin@exprivia.com"))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(gruppoUtenteRepository, gruppoEventPublisher);
    }

    @Test
    void delete_pulisceGruppiUtentePrimaDiEliminareUtente() {
        when(utenteRepository.findByEmail("admin@exprivia.com")).thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin@exprivia.com", RuoloUtente.ADMIN)));
        when(utenteRepository.findById(5L)).thenReturn(java.util.Optional.of(buildUser(5L, "User", "user@exprivia.com", RuoloUtente.USER)));

        utenteService.delete(5L, "admin@exprivia.com");

        var ordine = inOrder(gruppoUtenteRepository, utenteRepository);
        ordine.verify(gruppoUtenteRepository).deleteByIdUtente(5L);
        ordine.verify(utenteRepository).deleteById(5L);
    }

    @Test
    void delete_pubblicaEventoDopoEliminazione() {
        when(utenteRepository.findByEmail("admin@exprivia.com")).thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin@exprivia.com", RuoloUtente.ADMIN)));
        when(utenteRepository.findById(7L)).thenReturn(java.util.Optional.of(buildUser(7L, "User", "user7@exprivia.com", RuoloUtente.USER)));

        utenteService.delete(7L, "admin@exprivia.com");

        var ordine = inOrder(utenteRepository, gruppoEventPublisher);
        ordine.verify(utenteRepository).deleteById(7L);
        ordine.verify(gruppoEventPublisher).pubblicaEliminazioneUtente(any());
    }

    @Test
    void delete_eventoContieneSoloIdUtente() {
        when(utenteRepository.findByEmail("admin@exprivia.com")).thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin@exprivia.com", RuoloUtente.ADMIN)));
        when(utenteRepository.findById(9L)).thenReturn(java.util.Optional.of(buildUser(9L, "User", "user9@exprivia.com", RuoloUtente.USER)));

        utenteService.delete(9L, "admin@exprivia.com");

        ArgumentCaptor<UtenteEliminatoEvent> captor = ArgumentCaptor.forClass(UtenteEliminatoEvent.class);
        verify(gruppoEventPublisher).pubblicaEliminazioneUtente(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new UtenteEliminatoEvent(9L));
    }

    @Test
    void delete_utenteInesistente_nonPubblicaEvento() {
        when(utenteRepository.findByEmail("admin@exprivia.com")).thenReturn(java.util.Optional.of(buildUser(100L, "Admin", "admin@exprivia.com", RuoloUtente.ADMIN)));
        when(utenteRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        try { utenteService.delete(99L, "admin@exprivia.com"); } catch (EntityNotFoundException ignored) {}

        verify(gruppoEventPublisher, never()).pubblicaEliminazioneUtente(any());
    }

    private Utente buildUser(Long id, String fullName, String email, RuoloUtente ruolo) {
        Utente utente = new Utente();
        utente.setId(id);
        utente.setFullName(fullName);
        utente.setEmail(email);
        utente.setRuolo(ruolo);
        utente.setPasswordHash("hash");
        return utente;
    }
}
