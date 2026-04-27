package it.exprivia.prenotazioni;

import it.exprivia.prenotazioni.dto.CreatePrenotazioneRequest;
import it.exprivia.prenotazioni.dto.ExternalPostazioneResponse;
import it.exprivia.prenotazioni.dto.ExternalUtenteResponse;
import it.exprivia.prenotazioni.dto.UpdatePrenotazioneRequest;
import it.exprivia.prenotazioni.entity.Prenotazione;
import it.exprivia.prenotazioni.entity.RuoloUtente;
import it.exprivia.prenotazioni.entity.StatoPrenotazione;
import it.exprivia.prenotazioni.messaging.PrenotazioneEventPublisher;
import it.exprivia.prenotazioni.repository.PrenotazioneRepository;
import it.exprivia.prenotazioni.service.LocationServiceClient;
import it.exprivia.prenotazioni.service.PrenotazioneService;
import it.exprivia.prenotazioni.service.UtentiServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrenotazioneServiceTest {

    @Mock
    PrenotazioneRepository prenotazioneRepository;

    @Mock
    UtentiServiceClient utentiServiceClient;

    @Mock
    LocationServiceClient locationServiceClient;

    @Mock
    PrenotazioneEventPublisher prenotazioneEventPublisher;

    private PrenotazioneService prenotazioneService;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-04-27T08:00:00Z"),
            ZoneId.of("Europe/Rome")
    );

    @BeforeEach
    void setUp() {
        prenotazioneService = new PrenotazioneService(
                prenotazioneRepository,
                utentiServiceClient,
                locationServiceClient,
                prenotazioneEventPublisher,
                clock
        );
    }

    @Test
    void create_rifiutaPrenotazioneNelGiornoCorrente() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 27), LocalTime.of(9, 0), LocalTime.of(13, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(10L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("giorno successivo");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_rifiutaPrenotazioneNelWeekend() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 5, 2), LocalTime.of(9, 0), LocalTime.of(13, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(10L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sabato e la domenica");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_rifiutaSecondaPrenotazioneNelloStessoGiornoPerUtente() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(10L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());
        when(prenotazioneRepository.findFirstByUtenteIdAndDataPrenotazioneAndStatoOrderByOraInizioAsc(
                10L,
                request.getDataPrenotazione(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(Optional.of(buildPrenotazione(99L, 10L, 8L, LocalDate.of(2026, 4, 28), LocalTime.of(8, 0), LocalTime.of(12, 0))));

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hai gia' una prenotazione");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_rifiutaOverlapSullaStessaPostazione() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(10L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());
        when(prenotazioneRepository.findFirstByUtenteIdAndDataPrenotazioneAndStatoOrderByOraInizioAsc(
                10L,
                request.getDataPrenotazione(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(Optional.empty());
        when(prenotazioneRepository.existsActiveOverlap(
                7L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(true);

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gia' prenotata");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_consenteFasciaContiguaPerAltroUtente() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(13, 0), LocalTime.of(18, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());
        when(prenotazioneRepository.findFirstByUtenteIdAndDataPrenotazioneAndStatoOrderByOraInizioAsc(
                11L,
                request.getDataPrenotazione(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(Optional.empty());
        when(prenotazioneRepository.existsActiveOverlap(
                7L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(prenotazioneRepository.saveAndFlush(any(Prenotazione.class))).thenAnswer(invocation -> {
            Prenotazione prenotazione = invocation.getArgument(0);
            prenotazione.setId(101L);
            prenotazione.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            prenotazione.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return prenotazione;
        });

        var response = prenotazioneService.create(request, "Bearer token");

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getUtenteId()).isEqualTo(11L);
        assertThat(response.getPostazioneId()).isEqualTo(7L);
        assertThat(response.getOraInizio()).isEqualTo(LocalTime.of(13, 0));
        assertThat(response.getOraFine()).isEqualTo(LocalTime.of(18, 0));
        assertThat(response.getStato()).isEqualTo(StatoPrenotazione.CONFERMATA);
        verify(prenotazioneEventPublisher).pubblicaConferma(response);
    }

    @Test
    void update_rifiutaOverlapConAltraPrenotazioneEsistente() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 29), LocalTime.of(9, 0), LocalTime.of(13, 0));
        UpdatePrenotazioneRequest request = new UpdatePrenotazioneRequest(
                LocalDate.of(2026, 4, 29),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(prenotazioneRepository.findFirstByUtenteIdAndDataPrenotazioneAndStatoAndIdNotOrderByOraInizioAsc(
                11L,
                request.getDataPrenotazione(),
                StatoPrenotazione.CONFERMATA,
                22L
        )).thenReturn(Optional.empty());
        when(prenotazioneRepository.existsActiveOverlapExcludingId(
                22L,
                7L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(true);

        assertThatThrownBy(() -> prenotazioneService.update(22L, request, "Bearer token", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gia' prenotata");
    }

    @Test
    void update_aggiornaFasciaFuturaQuandoNonCiSonoConflitti() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 29), LocalTime.of(9, 0), LocalTime.of(13, 0));
        UpdatePrenotazioneRequest request = new UpdatePrenotazioneRequest(
                LocalDate.of(2026, 4, 30),
                LocalTime.of(10, 0),
                LocalTime.of(17, 0)
        );

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(prenotazioneRepository.findFirstByUtenteIdAndDataPrenotazioneAndStatoAndIdNotOrderByOraInizioAsc(
                11L,
                request.getDataPrenotazione(),
                StatoPrenotazione.CONFERMATA,
                22L
        )).thenReturn(Optional.empty());
        when(prenotazioneRepository.existsActiveOverlapExcludingId(
                22L,
                7L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(prenotazioneRepository.saveAndFlush(prenotazione)).thenReturn(prenotazione);

        var response = prenotazioneService.update(22L, request, "Bearer token", false);

        assertThat(response.getDataPrenotazione()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(response.getOraInizio()).isEqualTo(LocalTime.of(10, 0));
        assertThat(response.getOraFine()).isEqualTo(LocalTime.of(17, 0));
    }

    @Test
    void annulla_eliminaPrenotazioneFuturaDelProprietario() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));

        prenotazioneService.annulla(22L, "Bearer token", false);

        verify(prenotazioneRepository).delete(prenotazione);
        verify(prenotazioneEventPublisher).pubblicaAnnullamento(any());
    }

    private CreatePrenotazioneRequest buildCreateRequest(Long postazioneId, LocalDate data, LocalTime oraInizio, LocalTime oraFine) {
        return new CreatePrenotazioneRequest(postazioneId, data, oraInizio, oraFine);
    }

    private ExternalUtenteResponse buildUser(Long id, RuoloUtente ruolo) {
        return new ExternalUtenteResponse(id, "Mario Rossi", "mario.rossi@exprivia.com", ruolo);
    }

    private ExternalPostazioneResponse buildPostazione(Long id, String codice, String stato) {
        return new ExternalPostazioneResponse(
                id,
                codice,
                "layout-1",
                stato,
                BigDecimal.TEN,
                BigDecimal.ONE,
                3L,
                "Open Space"
        );
    }

    private Prenotazione buildPrenotazione(Long id,
                                           Long utenteId,
                                           Long postazioneId,
                                           LocalDate dataPrenotazione,
                                           LocalTime oraInizio,
                                           LocalTime oraFine) {
        Prenotazione prenotazione = new Prenotazione();
        prenotazione.setId(id);
        prenotazione.setUtenteId(utenteId);
        prenotazione.setUtenteEmail("utente@exprivia.com");
        prenotazione.setUtenteFullName("Utente Test");
        prenotazione.setPostazioneId(postazioneId);
        prenotazione.setPostazioneCodice("PS-007");
        prenotazione.setStanzaId(3L);
        prenotazione.setStanzaNome("Open Space");
        prenotazione.setDataPrenotazione(dataPrenotazione);
        prenotazione.setOraInizio(oraInizio);
        prenotazione.setOraFine(oraFine);
        prenotazione.setStato(StatoPrenotazione.CONFERMATA);
        prenotazione.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        prenotazione.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return prenotazione;
    }
}
