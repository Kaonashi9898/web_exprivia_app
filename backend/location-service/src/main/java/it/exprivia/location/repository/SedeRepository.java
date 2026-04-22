package it.exprivia.location.repository;

import it.exprivia.location.entity.Sede;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository JPA per l'entità {@link it.exprivia.location.entity.Sede}.
 */
public interface SedeRepository extends JpaRepository<Sede, Long> {

    // Filtra le sedi per città (usato dall'endpoint GET /api/sedi?citta=...)
    List<Sede> findByCitta(String citta);

    // Verifica se esiste già una sede con lo stesso nome nella stessa città (evita duplicati)
    boolean existsByNomeAndCitta(String nome, String citta);
}
