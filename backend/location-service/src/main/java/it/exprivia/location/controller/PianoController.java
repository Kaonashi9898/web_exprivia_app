package it.exprivia.location.controller;

import it.exprivia.location.dto.PianoRequest;
import it.exprivia.location.dto.PianoResponse;
import it.exprivia.location.service.PianoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST per la gestione dei piani di un edificio.
 *
 * Un piano appartiene a un edificio e può contenere stanze e una planimetria.
 * Ogni piano è identificato dal suo numero (es. 0 = piano terra, 1 = primo piano, ecc.).
 *
 * Endpoint (tutti sotto /api/piani):
 * - GET  /edificio/{id}  → piani di un edificio
 * - GET  /{id}           → dettaglio piano
 * - POST /               → crea un piano (solo ADMIN)
 * - PUT  /{id}           → aggiorna numero/edificio (solo ADMIN)
 * - DELETE /{id}         → elimina piano con stanze e postazioni (solo ADMIN)
 */
@RestController
@RequestMapping("/api/piani")
@RequiredArgsConstructor
public class PianoController {

    private final PianoService pianoService;

    /** Restituisce tutti i piani di un edificio. */
    @GetMapping("/edificio/{edificioId}")
    public ResponseEntity<List<PianoResponse>> getByEdificio(@PathVariable Long edificioId) {
        return ResponseEntity.ok(pianoService.findByEdificioId(edificioId));
    }

    /** Restituisce un piano per ID. */
    @GetMapping("/{id}")
    public ResponseEntity<PianoResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(pianoService.findById(id));
    }

    /** Crea un nuovo piano in un edificio. Restituisce 201 Created. */
    @PostMapping
    public ResponseEntity<PianoResponse> create(@Valid @RequestBody PianoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pianoService.create(request));
    }

    /** Aggiorna numero e edificio di un piano esistente. */
    @PutMapping("/{id}")
    public ResponseEntity<PianoResponse> update(@PathVariable Long id, @Valid @RequestBody PianoRequest request) {
        return ResponseEntity.ok(pianoService.update(id, request));
    }

    /** Elimina un piano (a cascata vengono eliminate stanze e postazioni). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        pianoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
