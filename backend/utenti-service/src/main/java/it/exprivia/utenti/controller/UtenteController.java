package it.exprivia.utenti.controller;

import it.exprivia.utenti.dto.RegisterRequest;
import it.exprivia.utenti.dto.UpdateUserRoleRequest;
import it.exprivia.utenti.dto.UtenteDTO;
import it.exprivia.utenti.service.UtenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/utenti")
@RequiredArgsConstructor
public class UtenteController {

    private final UtenteService utenteService;

    @GetMapping("/me")
    public ResponseEntity<UtenteDTO> getMe(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(utenteService.findByEmail(email));
    }

    @GetMapping
    public ResponseEntity<List<UtenteDTO>> findAll() {
        return ResponseEntity.ok(utenteService.findAll());
    }

    @PostMapping
    public ResponseEntity<UtenteDTO> create(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(utenteService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UtenteDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(utenteService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UtenteDTO> update(@PathVariable Long id, @Valid @RequestBody UtenteDTO dto) {
        return ResponseEntity.ok(utenteService.update(id, dto));
    }

    @PatchMapping("/{id}/ruolo")
    public ResponseEntity<UtenteDTO> updateRole(@PathVariable Long id,
                                                @Valid @RequestBody UpdateUserRoleRequest request) {
        return ResponseEntity.ok(utenteService.updateRole(id, request.getRuolo()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        utenteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
