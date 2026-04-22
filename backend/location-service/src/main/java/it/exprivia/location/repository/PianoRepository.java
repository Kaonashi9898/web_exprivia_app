package it.exprivia.location.repository;

import it.exprivia.location.entity.Piano;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository JPA per l'entità {@link it.exprivia.location.entity.Piano}.
 */
public interface PianoRepository extends JpaRepository<Piano, Long> {

    // Restituisce tutti i piani di un edificio
    List<Piano> findByEdificioId(Long edificioId);

    // Verifica se esiste già un piano con lo stesso numero nello stesso edificio
    boolean existsByNumeroAndEdificioId(Integer numero, Long edificioId);
}
