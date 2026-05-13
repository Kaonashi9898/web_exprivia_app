package it.exprivia.prenotazioni.repository;

import it.exprivia.prenotazioni.entity.PrenotazioneNotifica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrenotazioneNotificaRepository extends JpaRepository<PrenotazioneNotifica, Long> {

    List<PrenotazioneNotifica> findByUtenteIdAndReadAtIsNullOrderByCreatedAtAsc(Long utenteId);
}
