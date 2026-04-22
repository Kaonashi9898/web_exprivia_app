package it.exprivia.location.repository;

import it.exprivia.location.entity.Edificio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository JPA per l'entità {@link it.exprivia.location.entity.Edificio}.
 *
 * Oltre ai metodi CRUD standard, dichiara metodi personalizzati
 * per filtrare gli edifici per sede e verificare duplicati.
 */
public interface EdificioRepository extends JpaRepository<Edificio, Long> {

    // Restituisce tutti gli edifici di una sede specifica
    List<Edificio> findBySedeId(Long sedeId);

    // Verifica se esiste già un edificio con lo stesso nome nella stessa sede (evita duplicati)
    boolean existsByNomeAndSedeId(String nome, Long sedeId);
}
