package it.exprivia.utenti.controller;

import it.exprivia.utenti.dto.UtenteDTO;
import it.exprivia.utenti.entity.Gruppo;
import it.exprivia.utenti.service.GruppoService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST per la gestione dei gruppi utente.
 *
 * I gruppi servono per organizzare gli utenti (es. team o reparti aziendali).
 * Endpoint esposti:
 * - GET  /api/gruppi/me              → gruppi dell'utente autenticato
 * - GET  /api/gruppi                 → tutti i gruppi (solo ADMIN)
 * - POST /api/gruppi                 → crea un nuovo gruppo (solo ADMIN)
 * - DELETE /api/gruppi/{id}          → elimina un gruppo (solo ADMIN)
 * - POST /api/gruppi/{id}/utenti/{id} → aggiunge un utente a un gruppo (solo ADMIN)
 * - DELETE /api/gruppi/{id}/utenti/{id} → rimuove un utente da un gruppo (solo ADMIN)
 * - GET /api/gruppi/{id}/utenti      → lista utenti di un gruppo (solo ADMIN)
 */
@RestController
@RequestMapping("/api/gruppi")
@RequiredArgsConstructor
@Validated
public class GruppoController {

    private final GruppoService gruppoService;

    /**
     * Restituisce i gruppi a cui appartiene l'utente attualmente autenticato.
     * L'email viene estratta automaticamente dal token JWT tramite @AuthenticationPrincipal.
     */
    @GetMapping("/me")
    public ResponseEntity<List<Gruppo>> getMiei(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(gruppoService.getMiei(email));
    }

    /**
     * Crea un nuovo gruppo con il nome fornito come parametro query.
     * Esempio: POST /api/gruppi?nome=TeamAlpha
     */
    @PostMapping
    public ResponseEntity<Gruppo> crea(@RequestParam @NotBlank String nome) {
        return ResponseEntity.ok(gruppoService.crea(nome));
    }

    /**
     * Restituisce la lista di tutti i gruppi presenti nel sistema.
     */
    @GetMapping
    public ResponseEntity<List<Gruppo>> findAll() {
        return ResponseEntity.ok(gruppoService.findAll());
    }

    /**
     * Elimina un gruppo dato il suo ID.
     * Pubblica un evento RabbitMQ per notificare gli altri servizi dell'eliminazione.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> elimina(@PathVariable Long id) {
        gruppoService.elimina(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Aggiunge un utente esistente a un gruppo esistente.
     * Restituisce 400 se l'utente è già nel gruppo.
     */
    @PostMapping("/{idGruppo}/utenti/{idUtente}")
    public ResponseEntity<Void> aggiungiUtente(@PathVariable Long idGruppo, @PathVariable Long idUtente) {
        gruppoService.aggiungiUtente(idGruppo, idUtente);
        return ResponseEntity.ok().build();
    }

    /**
     * Rimuove un utente da un gruppo.
     * Restituisce 204 No Content se l'operazione va a buon fine.
     */
    @DeleteMapping("/{idGruppo}/utenti/{idUtente}")
    public ResponseEntity<Void> rimuoviUtente(@PathVariable Long idGruppo, @PathVariable Long idUtente) {
        gruppoService.rimuoviUtente(idGruppo, idUtente);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restituisce la lista degli utenti appartenenti a un gruppo specifico.
     */
    @GetMapping("/{idGruppo}/utenti")
    public ResponseEntity<List<UtenteDTO>> getUtenti(@PathVariable Long idGruppo) {
        return ResponseEntity.ok(gruppoService.getUtentiDelGruppo(idGruppo));
    }
}
