package it.exprivia.utenti.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.exprivia.utenti.entity.Utente;

import java.util.Optional;

/**
 * Repository JPA per l'entità {@link it.exprivia.utenti.entity.Utente}.
 *
 * Oltre ai metodi CRUD ereditati da JpaRepository, dichiara metodi
 * personalizzati basati sull'email, necessari per login e verifica duplicati.
 */
@Repository
public interface UtenteRepository extends JpaRepository<Utente, Long> {

    // Cerca un utente per email (usato durante il login e il recupero del profilo)
    Optional<Utente> findByEmail(String email);

    // Verifica se esiste già un utente con quell'email (usato durante la registrazione)
    boolean existsByEmail(String email);

    boolean existsByRuolo(it.exprivia.utenti.entity.RuoloUtente ruolo);
}
