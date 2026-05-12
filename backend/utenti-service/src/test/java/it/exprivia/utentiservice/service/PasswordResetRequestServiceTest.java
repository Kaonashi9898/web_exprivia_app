package it.exprivia.utentiservice.service;

import it.exprivia.utenti.dto.PasswordResetRequestResponse;
import it.exprivia.utenti.entity.PasswordResetRequest;
import it.exprivia.utenti.entity.PasswordResetRequestStatus;
import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
import it.exprivia.utenti.repository.PasswordResetRequestRepository;
import it.exprivia.utenti.repository.UtenteRepository;
import it.exprivia.utenti.service.PasswordResetRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetRequestServiceTest {

    @Mock PasswordResetRequestRepository passwordResetRequestRepository;
    @Mock UtenteRepository utenteRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks PasswordResetRequestService passwordResetRequestService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetRequestService, "cooldownMinutes", 15L);
    }

    @Test
    void createPublicRequest_nonCreaNullaPerUtenteInesistente() {
        when(utenteRepository.findByEmail("missing@exprivia.com")).thenReturn(Optional.empty());

        passwordResetRequestService.createPublicRequest("missing@exprivia.com");

        verify(passwordResetRequestRepository, never()).save(any(PasswordResetRequest.class));
    }

    @Test
    void createPublicRequest_nonCreaDuplicatoQuandoEsisteGiaUnaRichiestaOpen() {
        Utente user = buildUser(10L, "User", "user@exprivia.com", RuoloUtente.USER);
        when(utenteRepository.findByEmail("user@exprivia.com")).thenReturn(Optional.of(user));
        when(passwordResetRequestRepository.findFirstByEmailAndStatusOrderByRequestedAtDesc(
                "user@exprivia.com",
                PasswordResetRequestStatus.OPEN
        )).thenReturn(Optional.of(buildRequest(1L, "user@exprivia.com", PasswordResetRequestStatus.OPEN, OffsetDateTime.now(ZoneOffset.UTC))));

        passwordResetRequestService.createPublicRequest("user@exprivia.com");

        verify(passwordResetRequestRepository, never()).save(any(PasswordResetRequest.class));
    }

    @Test
    void createPublicRequest_rispettaCooldownTraRichiesteConsecutive() {
        OffsetDateTime fiveMinutesAgo = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        Utente user = buildUser(10L, "User", "user@exprivia.com", RuoloUtente.USER);
        when(utenteRepository.findByEmail("user@exprivia.com")).thenReturn(Optional.of(user));
        when(passwordResetRequestRepository.findFirstByEmailAndStatusOrderByRequestedAtDesc(
                "user@exprivia.com",
                PasswordResetRequestStatus.OPEN
        )).thenReturn(Optional.empty());
        when(passwordResetRequestRepository.findFirstByEmailOrderByRequestedAtDesc("user@exprivia.com"))
                .thenReturn(Optional.of(buildRequest(1L, "user@exprivia.com", PasswordResetRequestStatus.REJECTED, fiveMinutesAgo)));

        passwordResetRequestService.createPublicRequest("user@exprivia.com");

        verify(passwordResetRequestRepository, never()).save(any(PasswordResetRequest.class));
    }

    @Test
    void createPublicRequest_salvaRichiestaValidaNormalizzandoEmail() {
        Utente user = buildUser(10L, "User", "user@exprivia.com", RuoloUtente.USER);
        when(utenteRepository.findByEmail("user@exprivia.com")).thenReturn(Optional.of(user));
        when(passwordResetRequestRepository.findFirstByEmailAndStatusOrderByRequestedAtDesc(
                "user@exprivia.com",
                PasswordResetRequestStatus.OPEN
        )).thenReturn(Optional.empty());
        when(passwordResetRequestRepository.findFirstByEmailOrderByRequestedAtDesc("user@exprivia.com"))
                .thenReturn(Optional.empty());
        when(passwordResetRequestRepository.save(any(PasswordResetRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        passwordResetRequestService.createPublicRequest("  User@Exprivia.com ");

        ArgumentCaptor<PasswordResetRequest> captor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(passwordResetRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@exprivia.com");
        assertThat(captor.getValue().getStatus()).isEqualTo(PasswordResetRequestStatus.OPEN);
        assertThat(captor.getValue().getRequestedAt()).isNotNull();
    }

    @Test
    void findOpenRequests_receptionVedeSoloRichiestePerUserOGuest() {
        PasswordResetRequest userRequest = buildRequest(1L, "user@exprivia.com", PasswordResetRequestStatus.OPEN, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        PasswordResetRequest guestRequest = buildRequest(2L, "guest@exprivia.com", PasswordResetRequestStatus.OPEN, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(8));
        PasswordResetRequest managerRequest = buildRequest(3L, "manager@exprivia.com", PasswordResetRequestStatus.OPEN, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(6));
        when(utenteRepository.findByEmail("reception@exprivia.com"))
                .thenReturn(Optional.of(buildUser(90L, "Reception", "reception@exprivia.com", RuoloUtente.RECEPTION)));
        when(passwordResetRequestRepository.findByStatusOrderByRequestedAtAsc(PasswordResetRequestStatus.OPEN))
                .thenReturn(List.of(userRequest, guestRequest, managerRequest));
        when(utenteRepository.findByEmail("user@exprivia.com"))
                .thenReturn(Optional.of(buildUser(10L, "User", "user@exprivia.com", RuoloUtente.USER)));
        when(utenteRepository.findByEmail("guest@exprivia.com"))
                .thenReturn(Optional.of(buildUser(11L, "Guest", "guest@exprivia.com", RuoloUtente.GUEST)));
        when(utenteRepository.findByEmail("manager@exprivia.com"))
                .thenReturn(Optional.of(buildUser(12L, "Manager", "manager@exprivia.com", RuoloUtente.BUILDING_MANAGER)));

        List<PasswordResetRequestResponse> responses = passwordResetRequestService.findOpenRequests("reception@exprivia.com");

        assertThat(responses).extracting(PasswordResetRequestResponse::getEmail)
                .containsExactly("user@exprivia.com", "guest@exprivia.com");
    }

    @Test
    void completeRequest_adminAggiornaPasswordETracciaOperatore() {
        PasswordResetRequest request = buildRequest(7L, "user@exprivia.com", PasswordResetRequestStatus.OPEN, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(3));
        Utente admin = buildUser(1L, "Admin", "admin@exprivia.com", RuoloUtente.ADMIN);
        Utente target = buildUser(2L, "User", "user@exprivia.com", RuoloUtente.USER);
        when(passwordResetRequestRepository.findById(7L)).thenReturn(Optional.of(request));
        when(utenteRepository.findByEmail("admin@exprivia.com")).thenReturn(Optional.of(admin));
        when(utenteRepository.findByEmail("user@exprivia.com")).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("TempPass123")).thenReturn("encoded-pass");
        when(passwordResetRequestRepository.save(any(PasswordResetRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PasswordResetRequestResponse response =
                passwordResetRequestService.completeRequest(7L, "TempPass123", "admin@exprivia.com");

        assertThat(target.getPasswordHash()).isEqualTo("encoded-pass");
        assertThat(response.getStatus()).isEqualTo(PasswordResetRequestStatus.DONE);
        assertThat(response.getHandledByEmail()).isEqualTo("admin@exprivia.com");
        assertThat(response.getHandledAt()).isNotNull();
    }

    @Test
    void completeRequest_receptionNonPuoGestireBuildingManager() {
        PasswordResetRequest request = buildRequest(8L, "manager@exprivia.com", PasswordResetRequestStatus.OPEN, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2));
        when(passwordResetRequestRepository.findById(8L)).thenReturn(Optional.of(request));
        when(utenteRepository.findByEmail("reception@exprivia.com"))
                .thenReturn(Optional.of(buildUser(90L, "Reception", "reception@exprivia.com", RuoloUtente.RECEPTION)));
        when(utenteRepository.findByEmail("manager@exprivia.com"))
                .thenReturn(Optional.of(buildUser(12L, "Manager", "manager@exprivia.com", RuoloUtente.BUILDING_MANAGER)));

        assertThatThrownBy(() -> passwordResetRequestService.completeRequest(8L, "TempPass123", "reception@exprivia.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("USER o GUEST");

        verify(passwordResetRequestRepository, never()).save(any(PasswordResetRequest.class));
    }

    private PasswordResetRequest buildRequest(Long id,
                                              String email,
                                              PasswordResetRequestStatus status,
                                              OffsetDateTime requestedAt) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setId(id);
        request.setEmail(email);
        request.setStatus(status);
        request.setRequestedAt(requestedAt);
        return request;
    }

    private Utente buildUser(Long id, String fullName, String email, RuoloUtente ruolo) {
        Utente utente = new Utente();
        utente.setId(id);
        utente.setFullName(fullName);
        utente.setEmail(email);
        utente.setRuolo(ruolo);
        utente.setPasswordHash("old-hash");
        return utente;
    }
}
