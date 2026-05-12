package it.exprivia.utenti.service;

import it.exprivia.utenti.dto.PasswordResetRequestResponse;
import it.exprivia.utenti.entity.PasswordResetRequest;
import it.exprivia.utenti.entity.PasswordResetRequestStatus;
import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
import it.exprivia.utenti.repository.PasswordResetRequestRepository;
import it.exprivia.utenti.repository.UtenteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetRequestService {

    private static final Pattern EXPRIVIA_EMAIL_PATTERN =
            Pattern.compile("^[a-z0-9]+(?:[._-][a-z0-9]+)*@exprivia\\.com$", Pattern.CASE_INSENSITIVE);
    private static final List<RuoloUtente> RECEPTION_MANAGEABLE_ROLES = List.of(RuoloUtente.USER, RuoloUtente.GUEST);

    private final PasswordResetRequestRepository passwordResetRequestRepository;
    private final UtenteRepository utenteRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${password-reset-request.cooldown-minutes:15}")
    private long cooldownMinutes;

    @Transactional
    public void createPublicRequest(String rawEmail) {
        String normalizedEmail = normalizeEmail(rawEmail);
        if (normalizedEmail == null || !EXPRIVIA_EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return;
        }

        Utente utente = utenteRepository.findByEmail(normalizedEmail).orElse(null);
        if (utente == null || utente.getRuolo() == RuoloUtente.ADMIN) {
            return;
        }

        if (passwordResetRequestRepository
                .findFirstByEmailAndStatusOrderByRequestedAtDesc(normalizedEmail, PasswordResetRequestStatus.OPEN)
                .isPresent()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cooldownThreshold = now.minusMinutes(cooldownMinutes);
        Optional<PasswordResetRequest> latestRequest =
                passwordResetRequestRepository.findFirstByEmailOrderByRequestedAtDesc(normalizedEmail);
        if (latestRequest.isPresent() && latestRequest.get().getRequestedAt() != null
                && latestRequest.get().getRequestedAt().isAfter(cooldownThreshold)) {
            return;
        }

        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail(normalizedEmail);
        request.setRequestedAt(now);
        request.setStatus(PasswordResetRequestStatus.OPEN);
        try {
            passwordResetRequestRepository.save(request);
        } catch (DataIntegrityViolationException ignored) {
            // Una richiesta OPEN concorrente per la stessa email viene trattata come duplicato silenzioso.
        }
    }

    public List<PasswordResetRequestResponse> findOpenRequests(String actorEmail) {
        Utente actor = getActor(actorEmail);
        return passwordResetRequestRepository.findByStatusOrderByRequestedAtAsc(PasswordResetRequestStatus.OPEN).stream()
                .filter(request -> canActorViewRequest(actor, request))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PasswordResetRequestResponse completeRequest(Long requestId, String temporaryPassword, String actorEmail) {
        PasswordResetRequest request = getOpenRequest(requestId);
        Utente actor = getActor(actorEmail);
        Utente target = utenteRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Utente non trovato per la richiesta reset con email: " + request.getEmail()));

        ensureActorCanManageTarget(actor, target);
        target.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        utenteRepository.save(target);

        request.setStatus(PasswordResetRequestStatus.DONE);
        request.setHandledByEmail(normalizeEmail(actorEmail));
        request.setHandledAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toResponse(passwordResetRequestRepository.save(request), target);
    }

    @Transactional
    public PasswordResetRequestResponse rejectRequest(Long requestId, String actorEmail) {
        PasswordResetRequest request = getOpenRequest(requestId);
        Utente actor = getActor(actorEmail);
        Utente target = utenteRepository.findByEmail(request.getEmail()).orElse(null);
        if (target != null) {
            ensureActorCanManageTarget(actor, target);
        } else if (actor.getRuolo() != RuoloUtente.ADMIN && actor.getRuolo() != RuoloUtente.RECEPTION) {
            throw new ResponseStatusException(FORBIDDEN, "Permessi insufficienti per gestire la richiesta");
        }

        request.setStatus(PasswordResetRequestStatus.REJECTED);
        request.setHandledByEmail(normalizeEmail(actorEmail));
        request.setHandledAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toResponse(passwordResetRequestRepository.save(request), target);
    }

    private PasswordResetRequest getOpenRequest(Long requestId) {
        PasswordResetRequest request = passwordResetRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Richiesta reset password non trovata con id: " + requestId));
        if (request.getStatus() != PasswordResetRequestStatus.OPEN) {
            throw new IllegalArgumentException("La richiesta reset password e' gia' stata gestita");
        }
        return request;
    }

    private Utente getActor(String actorEmail) {
        String normalizedEmail = normalizeEmail(actorEmail);
        return utenteRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Operatore non trovato con email: " + normalizedEmail));
    }

    private void ensureActorCanManageTarget(Utente actor, Utente target) {
        if (target.getRuolo() == RuoloUtente.ADMIN) {
            throw new ResponseStatusException(FORBIDDEN, "Le richieste reset per ADMIN non sono gestibili da questo flusso");
        }
        if (actor.getRuolo() == RuoloUtente.ADMIN) {
            return;
        }
        if (actor.getRuolo() != RuoloUtente.RECEPTION) {
            throw new ResponseStatusException(FORBIDDEN, "Permessi insufficienti per gestire la richiesta");
        }
        if (!RECEPTION_MANAGEABLE_ROLES.contains(target.getRuolo())) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "RECEPTION puo' gestire reset password solo per utenti con ruolo USER o GUEST"
            );
        }
    }

    private PasswordResetRequestResponse toResponse(PasswordResetRequest request) {
        return toResponse(request, utenteRepository.findByEmail(request.getEmail()).orElse(null));
    }

    private PasswordResetRequestResponse toResponse(PasswordResetRequest request, Utente target) {
        return new PasswordResetRequestResponse(
                request.getId(),
                request.getEmail(),
                request.getRequestedAt(),
                request.getStatus(),
                request.getHandledByEmail(),
                request.getHandledAt(),
                target != null ? target.getId() : null,
                target != null ? target.getFullName() : null,
                target != null ? target.getRuolo() : null
        );
    }

    private boolean canActorViewRequest(Utente actor, PasswordResetRequest request) {
        if (actor.getRuolo() == RuoloUtente.ADMIN) {
            return true;
        }
        if (actor.getRuolo() != RuoloUtente.RECEPTION) {
            return false;
        }

        Utente target = utenteRepository.findByEmail(request.getEmail()).orElse(null);
        return target != null && RECEPTION_MANAGEABLE_ROLES.contains(target.getRuolo());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
