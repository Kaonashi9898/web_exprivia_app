package it.exprivia.location.repository;

import it.exprivia.location.entity.Planimetria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository JPA per l'entità {@link it.exprivia.location.entity.Planimetria}.
 *
 * La planimetria ha una relazione @OneToOne con Piano, quindi si cerca sempre per pianoId.
 */
public interface PlanimetriaRepository extends JpaRepository<Planimetria, Long> {

    // Recupera la planimetria di un piano (opzionale: un piano potrebbe non avere planimetria)
    Optional<Planimetria> findByPianoId(Long pianoId);

    // Verifica se un piano ha già una planimetria caricata
    boolean existsByPianoId(Long pianoId);
}
