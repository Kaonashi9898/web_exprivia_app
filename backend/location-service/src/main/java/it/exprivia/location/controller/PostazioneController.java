package it.exprivia.location.controller;

import it.exprivia.location.dto.PostazioneRequest;
import it.exprivia.location.dto.PostazioneResponse;
import it.exprivia.location.entity.StatoPostazione;
import it.exprivia.location.service.PostazioneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST per la gestione delle postazioni di lavoro.
 *
 * Una postazione è l'unità prenotabile del sistema.
 * Ogni postazione ha un codice univoco, uno stato e un collegamento opzionale
 * alla station del layout esportato dal PlanimetriaEditor.
 *
 * Endpoint (tutti sotto /api/postazioni):
 * - GET  /stanza/{id}             → postazioni di una stanza
 * - GET  /stanza/{id}/disponibili → solo le postazioni disponibili in una stanza
 * - GET  /{id}                    → dettaglio postazione
 * - GET  /{id}/disponibile        → booleano: la postazione è disponibile? (usato da prenotazioni-service)
 * - POST /                        → crea postazione (BUILDING_MANAGER o ADMIN)
 * - PUT  /{id}                    → aggiorna postazione completa (BUILDING_MANAGER o ADMIN)
 * - PATCH /{id}/stato             → aggiorna solo lo stato (BUILDING_MANAGER o ADMIN)
 * - DELETE /{id}                  → elimina postazione (BUILDING_MANAGER o ADMIN)
 */
@RestController
@RequestMapping("/api/postazioni")
@RequiredArgsConstructor
public class PostazioneController {

    private final PostazioneService postazioneService;

    /** Restituisce tutte le postazioni di una stanza. */
    @GetMapping("/stanza/{stanzaId}")
    public ResponseEntity<List<PostazioneResponse>> getByStanza(@PathVariable Long stanzaId) {
        return ResponseEntity.ok(postazioneService.findByStanzaId(stanzaId));
    }

    /** Restituisce tutte le postazioni di un piano. */
    @GetMapping("/piano/{pianoId}")
    public ResponseEntity<List<PostazioneResponse>> getByPiano(@PathVariable Long pianoId) {
        return ResponseEntity.ok(postazioneService.findByPianoId(pianoId));
    }

    /** Restituisce solo le postazioni con stato DISPONIBILE in una stanza. */
    @GetMapping("/stanza/{stanzaId}/disponibili")
    public ResponseEntity<List<PostazioneResponse>> getDisponibiliByStanza(@PathVariable Long stanzaId) {
        return ResponseEntity.ok(postazioneService.findDisponibiliByStanzaId(stanzaId));
    }

    /** Restituisce una postazione per ID. */
    @GetMapping("/{id}")
    public ResponseEntity<PostazioneResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(postazioneService.findById(id));
    }

    // Endpoint chiamato da prenotazioni-service via HTTP sincrono per verificare la disponibilità
    @GetMapping("/{id}/disponibile")
    public ResponseEntity<Boolean> isDisponibile(@PathVariable Long id) {
        return ResponseEntity.ok(postazioneService.isDisponibile(id));
    }

    /** Crea una nuova postazione. Restituisce 201 Created. */
    @PostMapping
    public ResponseEntity<PostazioneResponse> create(@Valid @RequestBody PostazioneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postazioneService.create(request));
    }

    /** Aggiorna tutti i campi di una postazione esistente. */
    @PutMapping("/{id}")
    public ResponseEntity<PostazioneResponse> update(@PathVariable Long id, @Valid @RequestBody PostazioneRequest request) {
        return ResponseEntity.ok(postazioneService.update(id, request));
    }

    // Endpoint dedicato per Building Manager: cambia solo lo stato senza modificare gli altri campi
    @PatchMapping("/{id}/stato")
    public ResponseEntity<PostazioneResponse> aggiornaStato(@PathVariable Long id, @RequestParam StatoPostazione stato) {
        return ResponseEntity.ok(postazioneService.aggiornaStato(id, stato));
    }

    /** Elimina una postazione. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postazioneService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
