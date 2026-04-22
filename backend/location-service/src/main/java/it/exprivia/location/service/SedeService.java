package it.exprivia.location.service;

import it.exprivia.location.dto.SedeRequest;
import it.exprivia.location.dto.SedeResponse;
import it.exprivia.location.entity.Postazione;
import it.exprivia.location.entity.Sede;
import it.exprivia.location.messaging.PlanimetriaEliminataEvent;
import it.exprivia.location.messaging.PlanimetriaEventPublisher;
import it.exprivia.location.repository.PostazioneRepository;
import it.exprivia.location.repository.SedeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service per la logica di business delle sedi.
 *
 * Gestisce le operazioni CRUD sulle sedi aziendali.
 * Verifica l'unicità della combinazione nome+città prima di creare una nuova sede.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SedeService {

    private final SedeRepository sedeRepository;
    private final PostazioneRepository postazioneRepository;
    private final PlanimetriaEventPublisher planimetriaEventPublisher;

    /** Restituisce tutte le sedi. */
    public List<SedeResponse> findAll() {
        return sedeRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Restituisce le sedi filtrate per città. */
    public List<SedeResponse> findByCitta(String citta) {
        return sedeRepository.findByCitta(citta).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Restituisce una sede per ID. */
    public SedeResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    /** Crea una nuova sede. Verifica che non esista già una sede con lo stesso nome nella stessa città. */
    @Transactional
    public SedeResponse create(SedeRequest request) {
        if (sedeRepository.existsByNomeAndCitta(request.getNome(), request.getCitta())) {
            throw new IllegalArgumentException("Sede già esistente con questo nome in questa città");
        }
        Sede sede = new Sede();
        sede.setNome(request.getNome());
        sede.setIndirizzo(request.getIndirizzo());
        sede.setCitta(request.getCitta());
        sede.setLatitudine(request.getLatitudine());
        sede.setLongitudine(request.getLongitudine());
        return toResponse(sedeRepository.save(sede));
    }

    /** Aggiorna i dati di una sede esistente. */
    @Transactional
    public SedeResponse update(Long id, SedeRequest request) {
        Sede sede = getOrThrow(id);
        sede.setNome(request.getNome());
        sede.setIndirizzo(request.getIndirizzo());
        sede.setCitta(request.getCitta());
        sede.setLatitudine(request.getLatitudine());
        sede.setLongitudine(request.getLongitudine());
        return toResponse(sedeRepository.save(sede));
    }

    /** Elimina una sede (a cascata vengono eliminati edifici, piani, stanze, postazioni). */
    @Transactional
    public void delete(Long id) {
        if (!sedeRepository.existsById(id)) {
            throw new EntityNotFoundException("Sede non trovata con id: " + id);
        }
        List<Long> postazioneIds = postazioneRepository.findByStanzaPianoEdificioSedeId(id).stream()
                .map(Postazione::getId)
                .toList();
        sedeRepository.deleteById(id);
        planimetriaEventPublisher.pubblicaEliminazione(new PlanimetriaEliminataEvent(null, postazioneIds));
    }

    // Metodo helper: cerca la sede o lancia eccezione 404
    private Sede getOrThrow(Long id) {
        return sedeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sede non trovata con id: " + id));
    }

    // Converte l'entità Sede nel DTO di risposta
    private SedeResponse toResponse(Sede sede) {
        return new SedeResponse(
                sede.getId(),
                sede.getNome(),
                sede.getIndirizzo(),
                sede.getCitta(),
                sede.getLatitudine(),
                sede.getLongitudine()
        );
    }
}
