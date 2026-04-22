package it.exprivia.location.controller;

import it.exprivia.location.dto.StanzaRequest;
import it.exprivia.location.dto.StanzaResponse;
import it.exprivia.location.service.StanzaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST per la gestione delle stanze.
 *
 * Una stanza appartiene a un piano e contiene una o più postazioni.
 * Il nome di una stanza deve essere univoco all'interno dello stesso piano.
 *
 * Endpoint (tutti sotto /api/stanze):
 * - GET  /piano/{id}  → stanze di un piano
 * - GET  /{id}        → dettaglio stanza
 * - POST /            → crea stanza (solo ADMIN)
 * - PUT  /{id}        → aggiorna stanza (solo ADMIN)
 * - DELETE /{id}      → elimina stanza con tutte le postazioni (solo ADMIN)
 */
@RestController
@RequestMapping("/api/stanze")
@RequiredArgsConstructor
public class StanzaController {

    private final StanzaService stanzaService;

    /** Restituisce tutte le stanze di un piano. */
    @GetMapping("/piano/{pianoId}")
    public ResponseEntity<List<StanzaResponse>> getByPiano(@PathVariable Long pianoId) {
        return ResponseEntity.ok(stanzaService.findByPianoId(pianoId));
    }

    /** Restituisce una stanza per ID. */
    @GetMapping("/{id}")
    public ResponseEntity<StanzaResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(stanzaService.findById(id));
    }

    /** Crea una nuova stanza. Restituisce 201 Created. */
    @PostMapping
    public ResponseEntity<StanzaResponse> create(@Valid @RequestBody StanzaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stanzaService.create(request));
    }

    /** Aggiorna nome e piano di una stanza esistente. */
    @PutMapping("/{id}")
    public ResponseEntity<StanzaResponse> update(@PathVariable Long id, @Valid @RequestBody StanzaRequest request) {
        return ResponseEntity.ok(stanzaService.update(id, request));
    }

    /** Elimina una stanza (a cascata vengono eliminate tutte le postazioni). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        stanzaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
