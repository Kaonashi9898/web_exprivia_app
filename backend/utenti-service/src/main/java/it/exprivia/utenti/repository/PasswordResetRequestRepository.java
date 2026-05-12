package it.exprivia.utenti.repository;

import it.exprivia.utenti.entity.PasswordResetRequest;
import it.exprivia.utenti.entity.PasswordResetRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    List<PasswordResetRequest> findByStatusOrderByRequestedAtAsc(PasswordResetRequestStatus status);

    Optional<PasswordResetRequest> findFirstByEmailAndStatusOrderByRequestedAtDesc(String email,
                                                                                   PasswordResetRequestStatus status);

    Optional<PasswordResetRequest> findFirstByEmailOrderByRequestedAtDesc(String email);
}
