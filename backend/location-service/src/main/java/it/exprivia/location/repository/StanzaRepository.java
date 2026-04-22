package it.exprivia.location.repository;

import it.exprivia.location.entity.Stanza;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA per l'entità {@link it.exprivia.location.entity.Stanza}.
 */
public interface StanzaRepository extends JpaRepository<Stanza, Long> {

    // Tutte le stanze di un piano
    List<Stanza> findByPianoId(Long pianoId);

    // Cerca una stanza per nome all'interno di uno specifico piano
    Optional<Stanza> findByNomeAndPianoId(String nome, Long pianoId);

    // Verifica se esiste già una stanza con lo stesso nome sullo stesso piano
    boolean existsByNomeAndPianoId(String nome, Long pianoId);
}
