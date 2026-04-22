package it.exprivia.location.service;

import it.exprivia.location.dto.EdificioRequest;
import it.exprivia.location.dto.EdificioResponse;
import it.exprivia.location.entity.Edificio;
import it.exprivia.location.entity.Postazione;
import it.exprivia.location.entity.Sede;
import it.exprivia.location.messaging.PlanimetriaEliminataEvent;
import it.exprivia.location.messaging.PlanimetriaEventPublisher;
import it.exprivia.location.repository.EdificioRepository;
import it.exprivia.location.repository.PostazioneRepository;
import it.exprivia.location.repository.SedeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service per la logica di business degli edifici.
 *
 * @Transactional(readOnly = true) a livello di classe significa che tutti i metodi
 * di lettura sono ottimizzati (Hibernate non controlla le modifiche agli oggetti).
 * I metodi di scrittura sovrascrivono questa impostazione con @Transactional standard.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EdificioService {

    private final EdificioRepository edificioRepository;
    private final SedeRepository sedeRepository;
    private final PostazioneRepository postazioneRepository;
    private final PlanimetriaEventPublisher planimetriaEventPublisher;

    /** Restituisce tutti gli edifici di una sede, verificando che la sede esista. */
    public List<EdificioResponse> findBySedeId(Long sedeId) {
        if (!sedeRepository.existsById(sedeId)) {
            throw new EntityNotFoundException("Sede non trovata con id: " + sedeId);
        }
        return edificioRepository.findBySedeId(sedeId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Restituisce un edificio per ID o lancia EntityNotFoundException. */
    public EdificioResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    /** Crea un nuovo edificio nella sede specificata. Verifica che non esista già un edificio con lo stesso nome. */
    @Transactional
    public EdificioResponse create(EdificioRequest request) {
        Sede sede = sedeRepository.findById(request.getSedeId())
                .orElseThrow(() -> new EntityNotFoundException("Sede non trovata con id: " + request.getSedeId()));
        if (edificioRepository.existsByNomeAndSedeId(request.getNome(), request.getSedeId())) {
            throw new IllegalArgumentException("Edificio già esistente con questo nome in questa sede");
        }
        Edificio edificio = new Edificio();
        edificio.setNome(request.getNome());
        edificio.setSede(sede);
        return toResponse(edificioRepository.save(edificio));
    }

    /** Aggiorna nome e sede di un edificio esistente. */
    @Transactional
    public EdificioResponse update(Long id, EdificioRequest request) {
        Edificio edificio = getOrThrow(id);
        Sede sede = sedeRepository.findById(request.getSedeId())
                .orElseThrow(() -> new EntityNotFoundException("Sede non trovata con id: " + request.getSedeId()));
        edificio.setNome(request.getNome());
        edificio.setSede(sede);
        return toResponse(edificioRepository.save(edificio));
    }

    /** Elimina un edificio. I piani, stanze e postazioni vengono eliminati a cascata. */
    @Transactional
    public void delete(Long id) {
        if (!edificioRepository.existsById(id)) {
            throw new EntityNotFoundException("Edificio non trovato con id: " + id);
        }
        List<Long> postazioneIds = postazioneRepository.findByStanzaPianoEdificioId(id).stream()
                .map(Postazione::getId)
                .toList();
        edificioRepository.deleteById(id);
        planimetriaEventPublisher.pubblicaEliminazione(new PlanimetriaEliminataEvent(null, postazioneIds));
    }

    // Metodo helper: cerca l'edificio o lancia eccezione 404
    private Edificio getOrThrow(Long id) {
        return edificioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Edificio non trovato con id: " + id));
    }

    // Metodo helper: converte l'entità Edificio nel DTO di risposta
    private EdificioResponse toResponse(Edificio e) {
        return new EdificioResponse(
                e.getId(),
                e.getNome(),
                e.getSede().getId(),
                e.getSede().getNome()
        );
    }
}
