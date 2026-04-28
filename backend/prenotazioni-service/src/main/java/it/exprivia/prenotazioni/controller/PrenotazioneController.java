package it.exprivia.prenotazioni.controller;

import it.exprivia.prenotazioni.dto.CreatePrenotazioneRequest;
import it.exprivia.prenotazioni.dto.DashboardPrenotazioneResponse;
import it.exprivia.prenotazioni.dto.PrenotazioneResponse;
import it.exprivia.prenotazioni.dto.UpdatePrenotazioneRequest;
import it.exprivia.prenotazioni.service.PrenotazioneService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/prenotazioni")
@RequiredArgsConstructor
public class PrenotazioneController {

    private final PrenotazioneService prenotazioneService;

    @PostMapping
    public ResponseEntity<PrenotazioneResponse> create(@Valid @RequestBody CreatePrenotazioneRequest request,
                                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prenotazioneService.create(request, authorizationHeader));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrenotazioneResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody UpdatePrenotazioneRequest request,
                                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                                       Authentication authentication) {
        return ResponseEntity.ok(prenotazioneService.update(id, request, authorizationHeader, hasOperationalAccess(authentication)));
    }

    @GetMapping("/mie")
    public ResponseEntity<List<PrenotazioneResponse>> findMine(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(prenotazioneService.findMine(authorizationHeader, data));
    }

    @GetMapping("/mie/dashboard")
    public ResponseEntity<List<DashboardPrenotazioneResponse>> findMineForDashboard(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(prenotazioneService.findMineForDashboard(authorizationHeader, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrenotazioneResponse> findById(@PathVariable Long id,
                                                         @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                                         Authentication authentication) {
        return ResponseEntity.ok(prenotazioneService.findById(id, authorizationHeader, hasOperationalAccess(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<PrenotazioneResponse>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) Long postazioneId) {
        return ResponseEntity.ok(prenotazioneService.findAll(data, postazioneId));
    }

    @GetMapping("/postazione/{postazioneId}")
    public ResponseEntity<List<PrenotazioneResponse>> findByPostazioneAndData(
            @PathVariable Long postazioneId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(prenotazioneService.findByPostazioneAndData(postazioneId, data));
    }

    @GetMapping("/postazione/{postazioneId}/disponibile")
    public ResponseEntity<Boolean> isDisponibile(
            @PathVariable Long postazioneId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime oraInizio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime oraFine) {
        return ResponseEntity.ok(prenotazioneService.isDisponibile(postazioneId, data, oraInizio, oraFine));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> annulla(@PathVariable Long id,
                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                        Authentication authentication) {
        prenotazioneService.annulla(id, authorizationHeader, hasOperationalAccess(authentication));
        return ResponseEntity.noContent().build();
    }

    private boolean hasOperationalAccess(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> authority.equals("ROLE_ADMIN")
                        || authority.equals("ROLE_RECEPTION")
                        || authority.equals("ROLE_BUILDING_MANAGER"));
    }
}
