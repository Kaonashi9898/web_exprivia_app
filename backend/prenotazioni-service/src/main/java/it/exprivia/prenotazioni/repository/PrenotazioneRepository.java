package it.exprivia.prenotazioni.repository;

import it.exprivia.prenotazioni.entity.Prenotazione;
import it.exprivia.prenotazioni.entity.StatoPrenotazione;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface PrenotazioneRepository extends JpaRepository<Prenotazione, Long>, JpaSpecificationExecutor<Prenotazione> {

    @Query("""
            select count(p) > 0
            from Prenotazione p
            where p.utenteId = :utenteId
              and p.dataPrenotazione = :dataPrenotazione
              and p.stato = :stato
              and :oraInizio < p.oraFine
              and :oraFine > p.oraInizio
            """)
    boolean existsActiveOverlapForUser(@Param("utenteId") Long utenteId,
                                       @Param("dataPrenotazione") LocalDate dataPrenotazione,
                                       @Param("oraInizio") LocalTime oraInizio,
                                       @Param("oraFine") LocalTime oraFine,
                                       @Param("stato") StatoPrenotazione stato);

    @Query("""
            select count(p) > 0
            from Prenotazione p
            where p.id <> :prenotazioneId
              and p.utenteId = :utenteId
              and p.dataPrenotazione = :dataPrenotazione
              and p.stato = :stato
              and :oraInizio < p.oraFine
              and :oraFine > p.oraInizio
            """)
    boolean existsActiveOverlapForUserExcludingId(@Param("prenotazioneId") Long prenotazioneId,
                                                  @Param("utenteId") Long utenteId,
                                                  @Param("dataPrenotazione") LocalDate dataPrenotazione,
                                                  @Param("oraInizio") LocalTime oraInizio,
                                                  @Param("oraFine") LocalTime oraFine,
                                                  @Param("stato") StatoPrenotazione stato);

    @Query("""
            select count(p) > 0
            from Prenotazione p
            where p.postazioneId = :postazioneId
              and p.dataPrenotazione = :dataPrenotazione
              and p.stato = :stato
              and :oraInizio < p.oraFine
              and :oraFine > p.oraInizio
            """)
    boolean existsActiveOverlapForPostazione(@Param("postazioneId") Long postazioneId,
                                             @Param("dataPrenotazione") LocalDate dataPrenotazione,
                                             @Param("oraInizio") LocalTime oraInizio,
                                             @Param("oraFine") LocalTime oraFine,
                                             @Param("stato") StatoPrenotazione stato);

    @Query("""
            select count(p) > 0
            from Prenotazione p
            where p.id <> :prenotazioneId
              and p.postazioneId = :postazioneId
              and p.dataPrenotazione = :dataPrenotazione
              and p.stato = :stato
              and :oraInizio < p.oraFine
              and :oraFine > p.oraInizio
            """)
    boolean existsActiveOverlapForPostazioneExcludingId(@Param("prenotazioneId") Long prenotazioneId,
                                                        @Param("postazioneId") Long postazioneId,
                                                        @Param("dataPrenotazione") LocalDate dataPrenotazione,
                                                        @Param("oraInizio") LocalTime oraInizio,
                                                        @Param("oraFine") LocalTime oraFine,
                                                        @Param("stato") StatoPrenotazione stato);

    @Query("""
            select count(p) > 0
            from Prenotazione p
            where p.meetingRoomStanzaId = :meetingRoomStanzaId
              and p.dataPrenotazione = :dataPrenotazione
              and p.stato = :stato
              and :oraInizio < p.oraFine
              and :oraFine > p.oraInizio
            """)
    boolean existsActiveOverlapForMeetingRoom(@Param("meetingRoomStanzaId") Long meetingRoomStanzaId,
                                              @Param("dataPrenotazione") LocalDate dataPrenotazione,
                                              @Param("oraInizio") LocalTime oraInizio,
                                              @Param("oraFine") LocalTime oraFine,
                                              @Param("stato") StatoPrenotazione stato);

    @Query("""
            select count(p) > 0
            from Prenotazione p
            where p.id <> :prenotazioneId
              and p.meetingRoomStanzaId = :meetingRoomStanzaId
              and p.dataPrenotazione = :dataPrenotazione
              and p.stato = :stato
              and :oraInizio < p.oraFine
              and :oraFine > p.oraInizio
            """)
    boolean existsActiveOverlapForMeetingRoomExcludingId(@Param("prenotazioneId") Long prenotazioneId,
                                                         @Param("meetingRoomStanzaId") Long meetingRoomStanzaId,
                                                         @Param("dataPrenotazione") LocalDate dataPrenotazione,
                                                         @Param("oraInizio") LocalTime oraInizio,
                                                         @Param("oraFine") LocalTime oraFine,
                                                         @Param("stato") StatoPrenotazione stato);

    List<Prenotazione> findByUtenteIdOrderByDataPrenotazioneDescOraInizioAsc(Long utenteId);

    List<Prenotazione> findByUtenteIdAndDataPrenotazioneOrderByOraInizioAsc(Long utenteId, LocalDate dataPrenotazione);

    List<Prenotazione> findByPostazioneIdAndDataPrenotazioneOrderByOraInizioAsc(Long postazioneId, LocalDate dataPrenotazione);

    List<Prenotazione> findByMeetingRoomStanzaIdAndDataPrenotazioneOrderByOraInizioAsc(Long meetingRoomStanzaId, LocalDate dataPrenotazione);

    List<Prenotazione> findByPostazioneIdIn(List<Long> postazioneIds);

    List<Prenotazione> findByUtenteIdAndStatoAndDataPrenotazioneGreaterThanEqual(Long utenteId,
                                                                                 StatoPrenotazione stato,
                                                                                 LocalDate dataPrenotazione);
}
