package it.exprivia.location.service;

import it.exprivia.location.dto.PianoRequest;
import it.exprivia.location.dto.PianoResponse;
import it.exprivia.location.entity.Edificio;
import it.exprivia.location.entity.Piano;
import it.exprivia.location.repository.EdificioRepository;
import it.exprivia.location.repository.PianoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service per la logica di business dei piani.
 *
 * Verifica che non esistano piani con lo stesso numero nello stesso edificio
 * prima di crearne uno nuovo.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PianoService {

    private final PianoRepository pianoRepository;
    private final EdificioRepository edificioRepository;
    private final PlanimetriaService planimetriaService;

    /** Restituisce tutti i piani di un edificio. Verifica che l'edificio esista. */
    public List<PianoResponse> findByEdificioId(Long edificioId) {
        if (!edificioRepository.existsById(edificioId)) {
            throw new EntityNotFoundException("Edificio non trovato con id: " + edificioId);
        }
        return pianoRepository.findByEdificioId(edificioId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Restituisce un piano per ID. */
    public PianoResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    /** Crea un nuovo piano nell'edificio specificato. Verifica che non esista già quel numero. */
    @Transactional
    public PianoResponse create(PianoRequest request) {
        Edificio edificio = edificioRepository.findById(request.getEdificioId())
                .orElseThrow(() -> new EntityNotFoundException("Edificio non trovato con id: " + request.getEdificioId()));
        if (pianoRepository.existsByNumeroAndEdificioId(request.getNumero(), request.getEdificioId())) {
            throw new IllegalArgumentException("Piano " + request.getNumero() + " già esistente in questo edificio");
        }
        Piano piano = new Piano();
        piano.setNumero(request.getNumero());
        piano.setNome(normalizeName(request.getNome()));
        piano.setEdificio(edificio);
        return toResponse(pianoRepository.save(piano));
    }

    /** Aggiorna numero e edificio di un piano esistente. */
    @Transactional
    public PianoResponse update(Long id, PianoRequest request) {
        Piano piano = getOrThrow(id);
        Edificio edificio = edificioRepository.findById(request.getEdificioId())
                .orElseThrow(() -> new EntityNotFoundException("Edificio non trovato con id: " + request.getEdificioId()));
        piano.setNumero(request.getNumero());
        piano.setNome(normalizeName(request.getNome()));
        piano.setEdificio(edificio);
        return toResponse(pianoRepository.save(piano));
    }

    /** Elimina un piano (stanze e postazioni vengono eliminate a cascata). */
    @Transactional
    public void delete(Long id) {
        if (!pianoRepository.existsById(id)) {
            throw new EntityNotFoundException("Piano non trovato con id: " + id);
        }
        planimetriaService.cleanupResourcesForPianoDeletion(id);
        pianoRepository.deleteById(id);
    }

    // Metodo helper: cerca il piano o lancia eccezione 404
    private Piano getOrThrow(Long id) {
        return pianoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Piano non trovato con id: " + id));
    }

    // Converte l'entità Piano in DTO di risposta
    private PianoResponse toResponse(Piano p) {
        return new PianoResponse(
                p.getId(),
                p.getNumero(),
                p.getNome(),
                p.getEdificio().getId(),
                p.getEdificio().getNome()
        );
    }

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
