package it.exprivia.location.service;

import it.exprivia.location.dto.PostazioneRequest;
import it.exprivia.location.dto.PostazioneResponse;
import it.exprivia.location.entity.Postazione;
import it.exprivia.location.entity.StatoPostazione;
import it.exprivia.location.entity.Stanza;
import it.exprivia.location.repository.PostazioneRepository;
import it.exprivia.location.repository.StanzaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service per la logica di business delle postazioni.
 *
 * Gestisce tutte le operazioni CRUD sulle postazioni e
 * l'endpoint di verifica disponibilità usato da prenotazioni-service.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostazioneService {

    private final PostazioneRepository postazioneRepository;
    private final StanzaRepository stanzaRepository;

    /** Restituisce tutte le postazioni di una stanza. */
    public List<PostazioneResponse> findByStanzaId(Long stanzaId) {
        if (!stanzaRepository.existsById(stanzaId)) {
            throw new EntityNotFoundException("Stanza non trovata con id: " + stanzaId);
        }
        return postazioneRepository.findByStanzaId(stanzaId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Restituisce solo le postazioni con stato DISPONIBILE in una stanza. */
    public List<PostazioneResponse> findDisponibiliByStanzaId(Long stanzaId) {
        return postazioneRepository.findByStanzaIdAndStato(stanzaId, StatoPostazione.DISPONIBILE).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Restituisce una postazione per ID. */
    public PostazioneResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    /**
     * Verifica se una postazione è disponibile.
     * Chiamato da prenotazioni-service tramite HTTP sincrono prima di creare una prenotazione.
     */
    public boolean isDisponibile(Long id) {
        Postazione p = getOrThrow(id);
        return p.getStato() == StatoPostazione.DISPONIBILE;
    }

    /**
     * Crea una nuova postazione.
     * Se stato non è specificato, usa DISPONIBILE come default.
     * Se accessibile non è specificato, usa false come default.
     */
    @Transactional
    public PostazioneResponse create(PostazioneRequest request) {
        Stanza stanza = stanzaRepository.findById(request.getStanzaId())
                .orElseThrow(() -> new EntityNotFoundException("Stanza non trovata con id: " + request.getStanzaId()));
        if (postazioneRepository.existsByCodice(request.getCodice())) {
            throw new IllegalArgumentException("Postazione già esistente con codice: " + request.getCodice());
        }
        Postazione postazione = new Postazione();
        postazione.setCodice(request.getCodice());
        postazione.setCadId(request.getCadId());
        postazione.setTipo(request.getTipo());
        postazione.setStato(request.getStato() != null ? request.getStato() : StatoPostazione.DISPONIBILE);
        postazione.setAccessibile(request.getAccessibile() != null ? request.getAccessibile() : Boolean.FALSE);
        postazione.setX(request.getX());
        postazione.setY(request.getY());
        postazione.setStanza(stanza);
        return toResponse(postazioneRepository.save(postazione));
    }

    /** Aggiorna tutti i campi di una postazione esistente. */
    @Transactional
    public PostazioneResponse update(Long id, PostazioneRequest request) {
        Postazione postazione = getOrThrow(id);
        Stanza stanza = stanzaRepository.findById(request.getStanzaId())
                .orElseThrow(() -> new EntityNotFoundException("Stanza non trovata con id: " + request.getStanzaId()));
        postazione.setCodice(request.getCodice());
        postazione.setCadId(request.getCadId());
        postazione.setTipo(request.getTipo());
        postazione.setStato(request.getStato());
        postazione.setAccessibile(request.getAccessibile());
        postazione.setX(request.getX());
        postazione.setY(request.getY());
        postazione.setStanza(stanza);
        return toResponse(postazioneRepository.save(postazione));
    }

    /**
     * Aggiorna solo lo stato di una postazione senza modificare gli altri campi.
     * Usato dal Building Manager per mettere postazioni in manutenzione, ecc.
     */
    @Transactional
    public PostazioneResponse aggiornaStato(Long id, StatoPostazione stato) {
        Postazione postazione = getOrThrow(id);
        postazione.setStato(stato);
        return toResponse(postazioneRepository.save(postazione));
    }

    /** Elimina una postazione. */
    @Transactional
    public void delete(Long id) {
        if (!postazioneRepository.existsById(id)) {
            throw new EntityNotFoundException("Postazione non trovata con id: " + id);
        }
        postazioneRepository.deleteById(id);
    }

    // Metodo helper: cerca la postazione o lancia eccezione 404
    private Postazione getOrThrow(Long id) {
        return postazioneRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Postazione non trovata con id: " + id));
    }

    // Converte l'entità Postazione nel DTO di risposta
    private PostazioneResponse toResponse(Postazione p) {
        return new PostazioneResponse(
                p.getId(),
                p.getCodice(),
                p.getCadId(),
                p.getTipo(),
                p.getStato(),
                p.getAccessibile(),
                p.getX(),
                p.getY(),
                p.getStanza().getId(),
                p.getStanza().getNome()
        );
    }
}
