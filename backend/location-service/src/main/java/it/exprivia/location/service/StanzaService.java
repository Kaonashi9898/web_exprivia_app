package it.exprivia.location.service;

import it.exprivia.location.dto.StanzaRequest;
import it.exprivia.location.dto.StanzaResponse;
import it.exprivia.location.entity.Piano;
import it.exprivia.location.entity.Stanza;
import it.exprivia.location.repository.PianoRepository;
import it.exprivia.location.repository.StanzaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service per la logica di business delle stanze.
 *
 * Verifica che non esistano stanze con lo stesso nome sullo stesso piano
 * prima di crearne una nuova (vincolo di unicità).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StanzaService {

    private final StanzaRepository stanzaRepository;
    private final PianoRepository pianoRepository;

    /** Restituisce tutte le stanze di un piano. */
    public List<StanzaResponse> findByPianoId(Long pianoId) {
        if (!pianoRepository.existsById(pianoId)) {
            throw new EntityNotFoundException("Piano non trovato con id: " + pianoId);
        }
        return stanzaRepository.findByPianoId(pianoId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Restituisce una stanza per ID. */
    public StanzaResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    /** Crea una nuova stanza in un piano. Verifica che non esista già una stanza con lo stesso nome sullo stesso piano. */
    @Transactional
    public StanzaResponse create(StanzaRequest request) {
        Piano piano = pianoRepository.findById(request.getPianoId())
                .orElseThrow(() -> new EntityNotFoundException("Piano non trovato con id: " + request.getPianoId()));
        if (stanzaRepository.existsByNomeAndPianoId(request.getNome(), request.getPianoId())) {
            throw new IllegalArgumentException("Stanza già esistente con questo nome su questo piano");
        }
        Stanza stanza = new Stanza();
        stanza.setNome(request.getNome());
        stanza.setPiano(piano);
        return toResponse(stanzaRepository.save(stanza));
    }

    /** Aggiorna nome e piano di una stanza esistente. */
    @Transactional
    public StanzaResponse update(Long id, StanzaRequest request) {
        Stanza stanza = getOrThrow(id);
        Piano piano = pianoRepository.findById(request.getPianoId())
                .orElseThrow(() -> new EntityNotFoundException("Piano non trovato con id: " + request.getPianoId()));
        stanza.setNome(request.getNome());
        stanza.setPiano(piano);
        return toResponse(stanzaRepository.save(stanza));
    }

    /** Elimina una stanza (le postazioni vengono eliminate a cascata). */
    @Transactional
    public void delete(Long id) {
        if (!stanzaRepository.existsById(id)) {
            throw new EntityNotFoundException("Stanza non trovata con id: " + id);
        }
        stanzaRepository.deleteById(id);
    }

    // Metodo helper: cerca la stanza o lancia eccezione 404
    private Stanza getOrThrow(Long id) {
        return stanzaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Stanza non trovata con id: " + id));
    }

    // Converte l'entità Stanza nel DTO di risposta
    private StanzaResponse toResponse(Stanza s) {
        return new StanzaResponse(
                s.getId(),
                s.getNome(),
                s.getPiano().getId(),
                s.getPiano().getNumero()
        );
    }
}
