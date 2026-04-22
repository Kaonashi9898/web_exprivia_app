package it.exprivia.location.controller;

import it.exprivia.location.dto.EdificioRequest;
import it.exprivia.location.dto.EdificioResponse;
import it.exprivia.location.service.EdificioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST per la gestione degli edifici.
 *
 * Un edificio appartiene a una sede e contiene uno o più piani.
 * La gerarchia è: Sede → Edificio → Piano → Stanza → Postazione
 *
 * Endpoint esposti (tutti sotto /api/edifici):
 * - GET  /sede/{sedeId}  → edifici di una sede
 * - GET  /{id}           → dettaglio edificio
 * - POST /               → crea un nuovo edificio (solo ADMIN)
 * - PUT  /{id}           → aggiorna un edificio (solo ADMIN)
 * - DELETE /{id}         → elimina un edificio con tutti i piani/stanze/postazioni (solo ADMIN)
 */
@RestController
@RequestMapping("/api/edifici")
@RequiredArgsConstructor
public class EdificioController {

    private final EdificioService edificioService;

    /** Restituisce tutti gli edifici appartenenti a una sede. */
    @GetMapping("/sede/{sedeId}")
    public ResponseEntity<List<EdificioResponse>> getBySede(@PathVariable Long sedeId) {
        return ResponseEntity.ok(edificioService.findBySedeId(sedeId));
    }

    /** Restituisce un singolo edificio per ID. */
    @GetMapping("/{id}")
    public ResponseEntity<EdificioResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(edificioService.findById(id));
    }

    /** Crea un nuovo edificio. Restituisce 201 Created. */
    @PostMapping
    public ResponseEntity<EdificioResponse> create(@Valid @RequestBody EdificioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(edificioService.create(request));
    }

    /** Aggiorna nome e sede di un edificio esistente. */
    @PutMapping("/{id}")
    public ResponseEntity<EdificioResponse> update(@PathVariable Long id, @Valid @RequestBody EdificioRequest request) {
        return ResponseEntity.ok(edificioService.update(id, request));
    }

    /** Elimina un edificio (a cascata vengono eliminati piani, stanze e postazioni). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        edificioService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
