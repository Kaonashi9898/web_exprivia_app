package it.exprivia.utenti.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.exprivia.utenti.entity.Gruppo;


/**
 * Repository JPA per l'entità {@link it.exprivia.utenti.entity.Gruppo}.
 *
 * Estende JpaRepository, che fornisce automaticamente i metodi CRUD base:
 * save(), findById(), findAll(), deleteById(), existsById(), ecc.
 * Non è necessario implementare nulla: Spring Data JPA genera il codice in automatico.
 */
@Repository
public interface GruppoRepository extends JpaRepository<Gruppo, Long> {
    boolean existsByNomeIgnoreCase(String nome);
    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
