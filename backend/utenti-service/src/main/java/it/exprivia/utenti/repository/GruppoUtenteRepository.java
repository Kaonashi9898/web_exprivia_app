package it.exprivia.utenti.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.exprivia.utenti.entity.GruppoUtente;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA per la tabella di join tra Gruppo e Utente.
 *
 * I metodi dichiarati qui vengono implementati automaticamente da Spring Data JPA
 * analizzando il nome del metodo (es. findByIdGruppo → SELECT * WHERE id_gruppo = ?).
 */
@Repository
public interface GruppoUtenteRepository extends JpaRepository<GruppoUtente, Long>{

    // Restituisce tutte le associazioni (righe) relative a un certo gruppo
    List<GruppoUtente> findByIdGruppo(Long idGruppo);

    // Restituisce tutte le associazioni relative a un certo utente
    List<GruppoUtente> findByIdUtente(Long idUtente);

    // Cerca un'associazione specifica gruppo-utente (usato per verificare duplicati)
    Optional<GruppoUtente> findByIdGruppoAndIdUtente(Long idGruppo, Long idUtente);

    // Verifica se un utente appartiene già a un gruppo senza caricare l'oggetto completo
    boolean existsByIdGruppoAndIdUtente(Long idGruppo, Long idUtente);

    // Elimina tutte le associazioni di un utente (usato quando l'utente viene cancellato)
    void deleteByIdUtente(Long idUtente);
}
