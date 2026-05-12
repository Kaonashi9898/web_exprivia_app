package it.exprivia.location.repository;

import it.exprivia.location.entity.GruppoPostazione;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository JPA per le associazioni gruppo-postazione.
 *
 * Usato sia dalla logica di business (GruppoPostazioneService)
 * che dai listener RabbitMQ per la pulizia dati a seguito di eliminazioni.
 */
public interface GruppoPostazioneRepository extends JpaRepository<GruppoPostazione, Long> {

    // Postazioni assegnate a un gruppo
    List<GruppoPostazione> findByGruppoId(Long gruppoId);

    // Gruppi che hanno accesso a una postazione specifica
    List<GruppoPostazione> findByPostazioneId(Long postazioneId);

    // Tutte le associazioni gruppo-postazione presenti in un piano
    List<GruppoPostazione> findByPostazioneStanzaPianoId(Long pianoId);

    // Verifica se esiste già l'associazione (evita duplicati)
    boolean existsByGruppoIdAndPostazioneId(Long gruppoId, Long postazioneId);

    // Elimina l'associazione specifica tra un gruppo e una postazione
    void deleteByGruppoIdAndPostazioneId(Long gruppoId, Long postazioneId);

    // Elimina tutte le associazioni di un gruppo (usato quando il gruppo viene eliminato)
    void deleteByGruppoId(Long gruppoId);
}
