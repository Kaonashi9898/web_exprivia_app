package it.exprivia.utenti.service;

import it.exprivia.utenti.dto.RegisterRequest;
import it.exprivia.utenti.dto.UtenteDTO;
import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
import it.exprivia.utenti.messaging.GruppoEventPublisher;
import it.exprivia.utenti.messaging.UtenteEliminatoEvent;
import it.exprivia.utenti.repository.GruppoUtenteRepository;
import it.exprivia.utenti.repository.UtenteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
@RequiredArgsConstructor
public class UtenteService {

    private static final Set<RuoloUtente> RUOLI_GESTIBILI_DA_RECEPTION =
            EnumSet.of(RuoloUtente.USER, RuoloUtente.GUEST);

    private final UtenteRepository utenteRepository;
    private final GruppoUtenteRepository gruppoUtenteRepository;
    private final GruppoEventPublisher gruppoEventPublisher;
    private final PasswordEncoder passwordEncoder;

    public List<UtenteDTO> findAll(String actorEmail) {
        List<UtenteDTO> utenti = utenteRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
        utenti.forEach(utente -> gruppoEventPublisher.pubblicaAccessoUtente(utente, normalizeEmail(actorEmail)));
        return utenti;
    }

    public UtenteDTO create(RegisterRequest request, String operatorEmail) {
        Utente operatore = getOperatore(operatorEmail);
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (utenteRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email gia' registrata");
        }
        RuoloUtente ruoloRichiesto = request.getRuolo() != null ? request.getRuolo() : RuoloUtente.USER;
        ensureOperatorePuoCreareRuolo(operatore, ruoloRichiesto);

        Utente utente = new Utente();
        utente.setFullName(request.getFullName());
        utente.setEmail(normalizedEmail);
        utente.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        utente.setRuolo(ruoloRichiesto);

        UtenteDTO dto = toDTO(utenteRepository.save(utente));
        gruppoEventPublisher.pubblicaCreazioneUtente(dto, normalizeEmail(operatorEmail));
        return dto;
    }

    public UtenteDTO findByEmail(String email, String actorEmail) {
        String normalizedEmail = normalizeEmail(email);
        Utente utente = utenteRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con email: " + normalizedEmail));
        UtenteDTO dto = toDTO(utente);
        gruppoEventPublisher.pubblicaAccessoUtente(dto, normalizeEmail(actorEmail));
        return dto;
    }

    public UtenteDTO findById(Long id, String actorEmail) {
        Utente utente = utenteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con id: " + id));
        UtenteDTO dto = toDTO(utente);
        gruppoEventPublisher.pubblicaAccessoUtente(dto, normalizeEmail(actorEmail));
        return dto;
    }

    public UtenteDTO update(Long id, UtenteDTO dto, String operatorEmail) {
        Utente operatore = getOperatore(operatorEmail);
        Utente utente = utenteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con id: " + id));
        ensureOperatorePuoGestireTarget(operatore, utente);
        String normalizedEmail = normalizeEmail(dto.getEmail());
        if (!normalizeEmail(utente.getEmail()).equals(normalizedEmail) && utenteRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email già registrata: " + normalizedEmail);
        }
        utente.setFullName(dto.getFullName());
        utente.setEmail(normalizedEmail);
        UtenteDTO updated = toDTO(utenteRepository.save(utente));
        gruppoEventPublisher.pubblicaAggiornamentoUtente(updated, normalizeEmail(operatorEmail));
        return updated;
    }

    public UtenteDTO updateRole(Long id, RuoloUtente ruolo, String operatorEmail) {
        Utente operatore = getOperatore(operatorEmail);
        Utente utente = utenteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con id: " + id));
        ensureOperatoreNonStaGestendoSeStesso(operatore, utente, "Non puoi modificare il tuo ruolo");
        ensureOperatorePuoGestireTarget(operatore, utente);
        ensureOperatorePuoAssegnareRuolo(operatore, ruolo);
        utente.setRuolo(ruolo);
        UtenteDTO updated = toDTO(utenteRepository.save(utente));
        gruppoEventPublisher.pubblicaAggiornamentoUtente(updated, normalizeEmail(operatorEmail));
        return updated;
    }

    @Transactional
    public void delete(Long id, String operatorEmail) {
        Utente operatore = getOperatore(operatorEmail);
        Utente utente = utenteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con id: " + id));
        ensureOperatoreNonStaGestendoSeStesso(operatore, utente, "Non puoi eliminare il tuo account");
        ensureOperatorePuoGestireTarget(operatore, utente);
        gruppoUtenteRepository.deleteByIdUtente(id);
        utenteRepository.deleteById(id);
        gruppoEventPublisher.pubblicaEliminazioneUtente(new UtenteEliminatoEvent(id));
    }

    private Utente getOperatore(String operatorEmail) {
        String normalizedEmail = normalizeEmail(operatorEmail);
        return utenteRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Operatore non trovato con email: " + normalizedEmail));
    }

    private void ensureOperatorePuoCreareRuolo(Utente operatore, RuoloUtente ruoloRichiesto) {
        if (operatore.getRuolo() == RuoloUtente.ADMIN) {
            return;
        }
        ensureOperatoreReception(operatore);
        ensureReceptionPuoGestireRuolo(ruoloRichiesto);
    }

    private void ensureOperatorePuoGestireTarget(Utente operatore, Utente target) {
        if (operatore.getRuolo() == RuoloUtente.ADMIN) {
            return;
        }
        ensureOperatoreReception(operatore);
        ensureReceptionPuoGestireRuolo(target.getRuolo());
    }

    private void ensureOperatorePuoAssegnareRuolo(Utente operatore, RuoloUtente ruoloRichiesto) {
        if (operatore.getRuolo() == RuoloUtente.ADMIN) {
            return;
        }
        ensureOperatoreReception(operatore);
        ensureReceptionPuoGestireRuolo(ruoloRichiesto);
    }

    private void ensureOperatoreReception(Utente operatore) {
        if (operatore.getRuolo() != RuoloUtente.RECEPTION) {
            throw new ResponseStatusException(FORBIDDEN, "Permessi insufficienti per gestire gli utenti");
        }
    }

    private void ensureReceptionPuoGestireRuolo(RuoloUtente ruolo) {
        if (!RUOLI_GESTIBILI_DA_RECEPTION.contains(ruolo)) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "RECEPTION puo' gestire solo utenti con ruolo USER o GUEST"
            );
        }
    }

    private void ensureOperatoreNonStaGestendoSeStesso(Utente operatore, Utente target, String message) {
        if (operatore.getId() != null && operatore.getId().equals(target.getId())) {
            throw new ResponseStatusException(FORBIDDEN, message);
        }
        if (Objects.equals(normalizeEmail(operatore.getEmail()), normalizeEmail(target.getEmail()))) {
            throw new ResponseStatusException(FORBIDDEN, message);
        }
    }

    private UtenteDTO toDTO(Utente utente) {
        return new UtenteDTO(
                utente.getId(),
                utente.getFullName(),
                utente.getEmail(),
                utente.getRuolo()
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
