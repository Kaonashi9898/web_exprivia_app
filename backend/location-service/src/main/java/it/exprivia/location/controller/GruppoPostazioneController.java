package it.exprivia.location.controller;

import it.exprivia.location.dto.GruppoPostazioneResponse;
import it.exprivia.location.service.GruppoPostazioneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST per la gestione delle associazioni tra gruppi e postazioni.
 *
 * Un "gruppo postazione" è un'assegnazione che indica quali postazioni
 * sono riservate a un determinato gruppo di utenti (es. un reparto aziendale).
 *
 * Nota: il gruppoId è un ID logico proveniente da utenti-service.
 * Non esiste una FK reale nel DB perché i due servizi hanno database separati.
 *
 * Endpoint (tutti sotto /api/gruppi-postazioni):
 * - GET  /gruppo/{id}               → postazioni assegnate a un gruppo
 * - GET  /postazione/{id}           → gruppi che possono usare una postazione
 * - POST /gruppo/{id}/postazione/{id} → aggiunge un'associazione
 * - DELETE /gruppo/{id}/postazione/{id} → rimuove un'associazione
 */
@RestController
@RequestMapping("/api/gruppi-postazioni")
@RequiredArgsConstructor
public class GruppoPostazioneController {

    private final GruppoPostazioneService gruppoPostazioneService;

    /** Restituisce tutte le postazioni assegnate a un gruppo. */
    @GetMapping("/gruppo/{gruppoId}")
    public ResponseEntity<List<GruppoPostazioneResponse>> getByGruppo(@PathVariable Long gruppoId) {
        return ResponseEntity.ok(gruppoPostazioneService.findByGruppoId(gruppoId));
    }

    /** Restituisce tutti i gruppi che hanno accesso a una certa postazione. */
    @GetMapping("/postazione/{postazioneId}")
    public ResponseEntity<List<GruppoPostazioneResponse>> getByPostazione(@PathVariable Long postazioneId) {
        return ResponseEntity.ok(gruppoPostazioneService.findByPostazioneId(postazioneId));
    }

    /** Restituisce tutte le associazioni gruppo-postazione di un piano. */
    @GetMapping("/piano/{pianoId}")
    public ResponseEntity<List<GruppoPostazioneResponse>> getByPiano(@PathVariable Long pianoId) {
        return ResponseEntity.ok(gruppoPostazioneService.findByPianoId(pianoId));
    }

    /** Crea un'associazione tra un gruppo e una postazione. Restituisce 201 Created. */
    @PostMapping("/gruppo/{gruppoId}/postazione/{postazioneId}")
    public ResponseEntity<GruppoPostazioneResponse> aggiungi(@PathVariable Long gruppoId, @PathVariable Long postazioneId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gruppoPostazioneService.aggiungi(gruppoId, postazioneId));
    }

    /** Rimuove un'associazione tra un gruppo e una postazione. Restituisce 204 No Content. */
    @DeleteMapping("/gruppo/{gruppoId}/postazione/{postazioneId}")
    public ResponseEntity<Void> rimuovi(@PathVariable Long gruppoId, @PathVariable Long postazioneId) {
        gruppoPostazioneService.rimuovi(gruppoId, postazioneId);
        return ResponseEntity.noContent().build();
    }
}
