package it.exprivia.location.repository;

import it.exprivia.location.entity.Postazione;
import it.exprivia.location.entity.StatoPostazione;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA per l'entità {@link it.exprivia.location.entity.Postazione}.
 */
public interface PostazioneRepository extends JpaRepository<Postazione, Long> {

    // Tutte le postazioni di una stanza
    List<Postazione> findByStanzaId(Long stanzaId);

    // Postazioni di una stanza filtrate per stato (es. solo DISPONIBILE)
    List<Postazione> findByStanzaIdAndStato(Long stanzaId, StatoPostazione stato);

    // Cerca per codice univoco (usato per evitare duplicati e per lookups)
    Optional<Postazione> findByCodice(String codice);

    // Cerca una postazione importata dal layout nello stesso piano usando il suo id tecnico
    Optional<Postazione> findByLayoutElementIdAndStanzaPianoId(String layoutElementId, Long pianoId);

    // Tutte le postazioni appartenenti a un piano
    List<Postazione> findByStanzaPianoId(Long pianoId);

    List<Postazione> findByStanzaPianoEdificioId(Long edificioId);

    List<Postazione> findByStanzaPianoEdificioSedeId(Long sedeId);

    // Verifica se esiste già una postazione con quel codice
    boolean existsByCodice(String codice);
}
