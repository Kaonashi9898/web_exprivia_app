package it.exprivia.location.controller;

import it.exprivia.location.dto.SedeRequest;
import it.exprivia.location.dto.SedeResponse;
import it.exprivia.location.service.SedeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST per la gestione delle sedi aziendali.
 *
 * Una sede è il livello più alto della gerarchia fisica:
 * Sede → Edificio → Piano → Stanza → Postazione
 *
 * Endpoint (tutti sotto /api/sedi):
 * - GET  /          → tutte le sedi, opzionalmente filtrate per città (?citta=Roma)
 * - GET  /{id}      → dettaglio sede
 * - POST /          → crea una sede (solo ADMIN)
 * - PUT  /{id}      → aggiorna una sede (solo ADMIN)
 * - DELETE /{id}    → elimina sede con tutti gli edifici/piani/stanze/postazioni (solo ADMIN)
 */
@RestController
@RequestMapping("/api/sedi")
@RequiredArgsConstructor
public class SedeController {

    private final SedeService sedeService;

    /**
     * Restituisce tutte le sedi. Se viene passato il parametro "citta",
     * filtra le sedi per quella città.
     * Esempio: GET /api/sedi?citta=Roma
     */
    @GetMapping
    public ResponseEntity<List<SedeResponse>> getAll(@RequestParam(required = false) String citta) {
        if (citta != null) {
            return ResponseEntity.ok(sedeService.findByCitta(citta));
        }
        return ResponseEntity.ok(sedeService.findAll());
    }

    /** Restituisce una sede per ID. */
    @GetMapping("/{id}")
    public ResponseEntity<SedeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(sedeService.findById(id));
    }

    /** Crea una nuova sede. Restituisce 201 Created. */
    @PostMapping
    public ResponseEntity<SedeResponse> create(@Valid @RequestBody SedeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sedeService.create(request));
    }

    /** Aggiorna i dati di una sede esistente. */
    @PutMapping("/{id}")
    public ResponseEntity<SedeResponse> update(@PathVariable Long id, @Valid @RequestBody SedeRequest request) {
        return ResponseEntity.ok(sedeService.update(id, request));
    }

    /** Elimina una sede (a cascata vengono eliminati tutti gli edifici, piani, stanze e postazioni). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sedeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
