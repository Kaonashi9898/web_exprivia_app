package it.exprivia.location.service;

import it.exprivia.location.dto.StanzaRequest;
import it.exprivia.location.dto.StanzaResponse;
import it.exprivia.location.entity.Piano;
import it.exprivia.location.entity.StatoPostazione;
import it.exprivia.location.entity.Stanza;
import it.exprivia.location.entity.TipoStanza;
import it.exprivia.location.messaging.MeetingRoomNonPrenotabileEvent;
import it.exprivia.location.messaging.PostazioneEventPublisher;
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
    private final PostazioneEventPublisher postazioneEventPublisher;

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
        stanza.setTipo(request.getTipo());
        stanza.setStato(resolveStato(request.getStato()));
        stanza.setLayoutElementId(request.getLayoutElementId());
        stanza.setXPct(request.getXPct());
        stanza.setYPct(request.getYPct());
        stanza.setPiano(piano);
        return toResponse(stanzaRepository.save(stanza));
    }

    /** Aggiorna i dati principali di una stanza esistente. */
    @Transactional
    public StanzaResponse update(Long id, StanzaRequest request) {
        Stanza stanza = getOrThrow(id);
        Piano piano = pianoRepository.findById(request.getPianoId())
                .orElseThrow(() -> new EntityNotFoundException("Piano non trovato con id: " + request.getPianoId()));
        StatoPostazione statoPrecedente = stanza.getStato();
        stanza.setNome(request.getNome());
        stanza.setTipo(request.getTipo());
        stanza.setStato(resolveStato(request.getStato()));
        stanza.setLayoutElementId(request.getLayoutElementId());
        stanza.setXPct(request.getXPct());
        stanza.setYPct(request.getYPct());
        stanza.setPiano(piano);
        Stanza saved = stanzaRepository.save(stanza);
        publishIfMeetingRoomBecameNonBookable(saved, statoPrecedente);
        return toResponse(saved);
    }

    /** Aggiorna solo lo stato di una stanza o meeting room. */
    @Transactional
    public StanzaResponse aggiornaStato(Long id, StatoPostazione stato) {
        Stanza stanza = getOrThrow(id);
        StatoPostazione statoPrecedente = stanza.getStato();
        stanza.setStato(resolveStato(stato));
        Stanza saved = stanzaRepository.save(stanza);
        publishIfMeetingRoomBecameNonBookable(saved, statoPrecedente);
        return toResponse(saved);
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
                s.getTipo(),
                resolveStato(s.getStato()),
                s.getLayoutElementId(),
                s.getXPct(),
                s.getYPct(),
                s.getPiano().getId(),
                s.getPiano().getNumero()
        );
    }

    private StatoPostazione resolveStato(StatoPostazione stato) {
        return stato != null ? stato : StatoPostazione.DISPONIBILE;
    }

    private void publishIfMeetingRoomBecameNonBookable(Stanza stanza, StatoPostazione statoPrecedente) {
        StatoPostazione statoAttuale = resolveStato(stanza.getStato());
        if (stanza.getTipo() != TipoStanza.MEETING_ROOM || statoAttuale == StatoPostazione.DISPONIBILE) {
            return;
        }
        if (statoPrecedente == statoAttuale) {
            return;
        }

        postazioneEventPublisher.pubblicaMeetingRoomNonPrenotabile(new MeetingRoomNonPrenotabileEvent(
                stanza.getId(),
                stanza.getNome(),
                statoAttuale
        ));
    }
}
