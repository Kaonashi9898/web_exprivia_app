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
import org.springframework.web.bind.annotation.CookieValue;
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
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/prenotazioni")
@RequiredArgsConstructor
public class PrenotazioneController {

    private static final String AUTH_COOKIE_NAME = "EXPRIVIA_AUTH_TOKEN";

    private final PrenotazioneService prenotazioneService;

    @PostMapping
    public ResponseEntity<PrenotazioneResponse> create(@Valid @RequestBody CreatePrenotazioneRequest request,
                                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                                       @CookieValue(value = AUTH_COOKIE_NAME, required = false) String authCookie) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prenotazioneService.create(request, resolveAuthorizationHeader(authorizationHeader, authCookie)));
    }

    @PostMapping("/meeting-room")
    public ResponseEntity<PrenotazioneResponse> createMeetingRoom(@Valid @RequestBody CreatePrenotazioneRequest request,
                                                                  @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                                                  @CookieValue(value = AUTH_COOKIE_NAME, required = false) String authCookie) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prenotazioneService.createMeetingRoom(request, resolveAuthorizationHeader(authorizationHeader, authCookie)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrenotazioneResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody UpdatePrenotazioneRequest request,
                                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                                       @CookieValue(value = AUTH_COOKIE_NAME, required = false) String authCookie,
                                                       Authentication authentication) {
        return ResponseEntity.ok(prenotazioneService.update(id, request, resolveAuthorizationHeader(authorizationHeader, authCookie), hasAdminOverride(authentication)));
    }

    @GetMapping("/mie")
    public ResponseEntity<List<PrenotazioneResponse>> findMine(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @CookieValue(value = AUTH_COOKIE_NAME, required = false) String authCookie,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(prenotazioneService.findMine(resolveAuthorizationHeader(authorizationHeader, authCookie), data));
    }

    @GetMapping("/mie/dashboard")
    public ResponseEntity<List<DashboardPrenotazioneResponse>> findMineForDashboard(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @CookieValue(value = AUTH_COOKIE_NAME, required = false) String authCookie,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(prenotazioneService.findMineForDashboard(resolveAuthorizationHeader(authorizationHeader, authCookie), data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrenotazioneResponse> findById(@PathVariable Long id,
                                                         @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                                         @CookieValue(value = AUTH_COOKIE_NAME, required = false) String authCookie,
                                                         Authentication authentication) {
        return ResponseEntity.ok(prenotazioneService.findById(id, resolveAuthorizationHeader(authorizationHeader, authCookie), hasAdminOverride(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<PrenotazioneResponse>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) Long postazioneId,
            @RequestParam(required = false) Long meetingRoomStanzaId) {
        return ResponseEntity.ok(prenotazioneService.findAll(data, postazioneId, meetingRoomStanzaId));
    }

    @GetMapping("/risorse")
    public ResponseEntity<List<PrenotazioneResponse>> findByResources(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) List<Long> postazioneIds,
            @RequestParam(required = false) List<Long> meetingRoomStanzaIds,
            Authentication authentication) {
        return ResponseEntity.ok(prenotazioneService.findByResources(
                data,
                postazioneIds,
                meetingRoomStanzaIds,
                hasOperationalBookingAccess(authentication)
        ));
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

    @GetMapping("/meeting-room/{stanzaId}")
    public ResponseEntity<List<PrenotazioneResponse>> findByMeetingRoomAndData(
            @PathVariable Long stanzaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(prenotazioneService.findByMeetingRoomAndData(stanzaId, data));
    }

    @GetMapping("/meeting-room/{stanzaId}/disponibile")
    public ResponseEntity<Boolean> isMeetingRoomDisponibile(
            @PathVariable Long stanzaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime oraInizio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime oraFine) {
        return ResponseEntity.ok(prenotazioneService.isMeetingRoomDisponibile(stanzaId, data, oraInizio, oraFine));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> annulla(@PathVariable Long id,
                                        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                        @CookieValue(value = AUTH_COOKIE_NAME, required = false) String authCookie,
                                        Authentication authentication) {
        prenotazioneService.annulla(id, resolveAuthorizationHeader(authorizationHeader, authCookie), hasAdminOverride(authentication));
        return ResponseEntity.noContent().build();
    }

    private String resolveAuthorizationHeader(String authorizationHeader, String authCookie) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            return authorizationHeader;
        }
        if (authCookie != null && !authCookie.isBlank()) {
            return "Bearer " + authCookie;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token di autenticazione mancante");
    }

    private boolean hasAdminOverride(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> authority.equals("ROLE_ADMIN"));
    }

    private boolean hasOperationalBookingAccess(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority ->
                        authority.equals("ROLE_ADMIN")
                                || authority.equals("ROLE_BUILDING_MANAGER")
                                || authority.equals("ROLE_RECEPTION"));
    }
}
