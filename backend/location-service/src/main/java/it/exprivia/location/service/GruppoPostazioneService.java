package it.exprivia.location.service;

import it.exprivia.location.dto.GruppoPostazioneResponse;
import it.exprivia.location.entity.GruppoPostazione;
import it.exprivia.location.entity.Postazione;
import it.exprivia.location.repository.GruppoPostazioneRepository;
import it.exprivia.location.repository.PostazioneRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service per la gestione delle associazioni tra gruppi utenti e postazioni.
 *
 * Permette di "riservare" postazioni a specifici gruppi.
 * Il gruppoId è un ID logico proveniente da utenti-service (non verificato qui).
 */
@Service
@RequiredArgsConstructor
public class GruppoPostazioneService {

    private final GruppoPostazioneRepository gruppoPostazioneRepository;
    private final PostazioneRepository postazioneRepository;

    /** Restituisce tutte le postazioni assegnate a un gruppo. */
    public List<GruppoPostazioneResponse> findByGruppoId(Long gruppoId) {
        return gruppoPostazioneRepository.findByGruppoId(gruppoId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Restituisce tutti i gruppi che hanno accesso a una postazione. */
    public List<GruppoPostazioneResponse> findByPostazioneId(Long postazioneId) {
        return gruppoPostazioneRepository.findByPostazioneId(postazioneId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Crea una nuova associazione gruppo-postazione.
     * Verifica che la postazione esista e che l'associazione non sia già presente.
     */
    public GruppoPostazioneResponse aggiungi(Long gruppoId, Long postazioneId) {
        Postazione postazione = postazioneRepository.findById(postazioneId)
                .orElseThrow(() -> new EntityNotFoundException("Postazione non trovata con id: " + postazioneId));
        if (gruppoPostazioneRepository.existsByGruppoIdAndPostazioneId(gruppoId, postazioneId)) {
            throw new IllegalArgumentException("Associazione già esistente");
        }
        GruppoPostazione gp = new GruppoPostazione();
        gp.setGruppoId(gruppoId);
        gp.setPostazione(postazione);
        return toResponse(gruppoPostazioneRepository.save(gp));
    }

    /** Rimuove un'associazione gruppo-postazione esistente. */
    @Transactional
    public void rimuovi(Long gruppoId, Long postazioneId) {
        if (!gruppoPostazioneRepository.existsByGruppoIdAndPostazioneId(gruppoId, postazioneId)) {
            throw new EntityNotFoundException("Associazione non trovata");
        }
        gruppoPostazioneRepository.deleteByGruppoIdAndPostazioneId(gruppoId, postazioneId);
    }

    // Converte l'entità nel DTO di risposta
    private GruppoPostazioneResponse toResponse(GruppoPostazione gp) {
        return new GruppoPostazioneResponse(
                gp.getId(),
                gp.getGruppoId(),
                gp.getPostazione().getId(),
                gp.getPostazione().getCodice()
        );
    }
}
