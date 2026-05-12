package it.exprivia.utenti.dto;

import it.exprivia.utenti.entity.PasswordResetRequestStatus;
import it.exprivia.utenti.entity.RuoloUtente;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequestResponse {

    private Long id;
    private String email;
    private OffsetDateTime requestedAt;
    private PasswordResetRequestStatus status;
    private String handledByEmail;
    private OffsetDateTime handledAt;
    private Long userId;
    private String userFullName;
    private RuoloUtente userRole;
}
