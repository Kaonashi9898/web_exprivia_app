package it.exprivia.utenti.controller;

import it.exprivia.utenti.dto.PasswordResetRequestCompleteRequest;
import it.exprivia.utenti.dto.PasswordResetRequestResponse;
import it.exprivia.utenti.service.PasswordResetRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/password-reset-requests")
@RequiredArgsConstructor
public class PasswordResetRequestController {

    private final PasswordResetRequestService passwordResetRequestService;

    @GetMapping
    public ResponseEntity<List<PasswordResetRequestResponse>> findOpenRequests(@AuthenticationPrincipal String actorEmail) {
        return ResponseEntity.ok(passwordResetRequestService.findOpenRequests(actorEmail));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<PasswordResetRequestResponse> completeRequest(@PathVariable Long id,
                                                                        @Valid @RequestBody PasswordResetRequestCompleteRequest request,
                                                                        @AuthenticationPrincipal String actorEmail) {
        return ResponseEntity.ok(
                passwordResetRequestService.completeRequest(id, request.getTemporaryPassword(), actorEmail)
        );
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PasswordResetRequestResponse> rejectRequest(@PathVariable Long id,
                                                                      @AuthenticationPrincipal String actorEmail) {
        return ResponseEntity.ok(passwordResetRequestService.rejectRequest(id, actorEmail));
    }
}
