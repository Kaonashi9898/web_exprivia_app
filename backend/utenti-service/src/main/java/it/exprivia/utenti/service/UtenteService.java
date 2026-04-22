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

import java.util.List;

@Service
@RequiredArgsConstructor
public class UtenteService {

    private final UtenteRepository utenteRepository;
    private final GruppoUtenteRepository gruppoUtenteRepository;
    private final GruppoEventPublisher gruppoEventPublisher;
    private final PasswordEncoder passwordEncoder;

    public List<UtenteDTO> findAll() {
        return utenteRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public UtenteDTO create(RegisterRequest request) {
        if (utenteRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email gia' registrata");
        }

        Utente utente = new Utente();
        utente.setFullName(request.getFullName());
        utente.setEmail(request.getEmail());
        utente.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        utente.setRuolo(request.getRuolo() != null ? request.getRuolo() : RuoloUtente.USER);

        return toDTO(utenteRepository.save(utente));
    }

    public UtenteDTO findByEmail(String email) {
        Utente utente = utenteRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con email: " + email));
        return toDTO(utente);
    }

    public UtenteDTO findById(Long id) {
        Utente utente = utenteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con id: " + id));
        return toDTO(utente);
    }

    public UtenteDTO update(Long id, UtenteDTO dto) {
        Utente utente = utenteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con id: " + id));
        utente.setFullName(dto.getFullName());
        utente.setEmail(dto.getEmail());
        return toDTO(utenteRepository.save(utente));
    }

    public UtenteDTO updateRole(Long id, RuoloUtente ruolo) {
        Utente utente = utenteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con id: " + id));
        utente.setRuolo(ruolo);
        return toDTO(utenteRepository.save(utente));
    }

    public void delete(Long id) {
        if (!utenteRepository.existsById(id)) {
            throw new EntityNotFoundException("Utente non trovato con id: " + id);
        }
        gruppoUtenteRepository.deleteByIdUtente(id);
        utenteRepository.deleteById(id);
        gruppoEventPublisher.pubblicaEliminazioneUtente(new UtenteEliminatoEvent(id));
    }

    private UtenteDTO toDTO(Utente utente) {
        return new UtenteDTO(
                utente.getId(),
                utente.getFullName(),
                utente.getEmail(),
                utente.getRuolo()
        );
    }
}
