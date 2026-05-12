package it.exprivia.prenotazioni;

import it.exprivia.prenotazioni.dto.CreatePrenotazioneRequest;
import it.exprivia.prenotazioni.dto.ExternalPostazioneResponse;
import it.exprivia.prenotazioni.dto.ExternalStanzaResponse;
import it.exprivia.prenotazioni.dto.ExternalUtenteResponse;
import it.exprivia.prenotazioni.dto.UpdatePrenotazioneRequest;
import it.exprivia.prenotazioni.entity.Prenotazione;
import it.exprivia.prenotazioni.entity.RuoloUtente;
import it.exprivia.prenotazioni.entity.StatoPrenotazione;
import it.exprivia.prenotazioni.entity.TipoRisorsaPrenotata;
import it.exprivia.prenotazioni.messaging.PrenotazioneEventPublisher;
import it.exprivia.prenotazioni.repository.PrenotazioneGroupAccessCacheRepository;
import it.exprivia.prenotazioni.repository.PrenotazioneRepository;
import it.exprivia.prenotazioni.service.LocationServiceClient;
import it.exprivia.prenotazioni.service.PrenotazioneService;
import it.exprivia.prenotazioni.service.UtentiServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrenotazioneServiceTest {

    @Mock
    PrenotazioneRepository prenotazioneRepository;

    @Mock
    PrenotazioneGroupAccessCacheRepository prenotazioneGroupAccessCacheRepository;

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
                prenotazioneGroupAccessCacheRepository,
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

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("giorno successivo");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_rifiutaPrenotazioneNelWeekend() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 5, 2), LocalTime.of(9, 0), LocalTime.of(13, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(10L, RuoloUtente.USER));

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sabato e la domenica");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_rifiutaPrenotazioneFuoriDallaFasciaNoveDiciotto() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(8, 0), LocalTime.of(12, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(10L, RuoloUtente.USER));

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("09:00 e le 18:00");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_rifiutaPrenotazioneSovrappostaPerUtente() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(10L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());
        when(utentiServiceClient.getCurrentUserGroupIds("Bearer token")).thenReturn(List.of());
        when(prenotazioneRepository.existsActiveOverlapForUser(
                10L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(true);

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gia' una prenotazione");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_rifiutaOverlapSullaStessaPostazione() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(10L, RuoloUtente.USER));
        when(prenotazioneRepository.existsActiveOverlapForUser(
                10L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());
        when(utentiServiceClient.getCurrentUserGroupIds("Bearer token")).thenReturn(List.of());
        when(prenotazioneRepository.existsActiveOverlapForPostazione(
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
    void create_consentePiuPrenotazioniNelloStessoGiornoQuandoNonSovrapposte() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(13, 0), LocalTime.of(18, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(prenotazioneRepository.existsActiveOverlapForUser(
                11L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());
        when(utentiServiceClient.getCurrentUserGroupIds("Bearer token")).thenReturn(List.of(101L));
        when(prenotazioneRepository.existsActiveOverlapForPostazione(
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
        assertThat(response.getTipoRisorsaPrenotata()).isEqualTo(TipoRisorsaPrenotata.POSTAZIONE);
        assertThat(response.getPostazioneId()).isEqualTo(7L);
        assertThat(response.getOraInizio()).isEqualTo(LocalTime.of(13, 0));
        assertThat(response.getOraFine()).isEqualTo(LocalTime.of(18, 0));
        assertThat(response.getStato()).isEqualTo(StatoPrenotazione.CONFERMATA);
        verify(prenotazioneEventPublisher).pubblicaConferma(response);
    }

    @Test
    void create_rifiutaPrenotazioneQuandoUtenteENonCondivideGruppiConLaPostazione() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(13, 0), LocalTime.of(18, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of(10L, 20L));
        when(utentiServiceClient.getCurrentUserGroupIds("Bearer token")).thenReturn(List.of(30L, 40L));

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Non sei autorizzato");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
        verify(prenotazioneGroupAccessCacheRepository).replacePostazioneGroups(7L, List.of(10L, 20L));
        verify(prenotazioneGroupAccessCacheRepository).replaceUserGroups(11L, List.of(30L, 40L));
    }

    @Test
    void create_rifiutaRuoloNonAbilitato() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(13, 0), LocalTime.of(18, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.GUEST));

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).contains("non puo' creare prenotazioni");
                });

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_rifiutaRichiestaPostazioneIncoerente() {
        CreatePrenotazioneRequest request = new CreatePrenotazioneRequest(null, 3L, LocalDate.of(2026, 4, 28), LocalTime.of(13, 0), LocalTime.of(18, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("una sola postazione");
    }

    @Test
    void create_rifiutaPostazioneNonPrenotabileNelloStatoAttuale() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(13, 0), LocalTime.of(18, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "MANUTENZIONE"));

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non e' prenotabile");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_traduceViolazioneIntegritaGruppiInForbidden() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(13, 0), LocalTime.of(18, 0));
        stubCreatePostazioneFlow(request, 11L);
        when(prenotazioneRepository.saveAndFlush(any(Prenotazione.class)))
                .thenThrow(new DataIntegrityViolationException("vincolo", new RuntimeException("nessun gruppo condiviso")));

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).contains("Non sei autorizzato");
                });
    }

    @Test
    void create_traduceViolazioneIntegritaGenericaInMessaggioUtenteComprensibile() {
        CreatePrenotazioneRequest request = buildCreateRequest(7L, LocalDate.of(2026, 4, 28), LocalTime.of(13, 0), LocalTime.of(18, 0));
        stubCreatePostazioneFlow(request, 11L);
        when(prenotazioneRepository.saveAndFlush(any(Prenotazione.class)))
                .thenThrow(new DataIntegrityViolationException("vincolo", new RuntimeException("duplicate key value")));

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflitto di prenotazione");
    }

    @Test
    void createMeetingRoom_confermaPrenotazioneSalaRiunioni() {
        CreatePrenotazioneRequest request = buildMeetingRoomRequest(3L, LocalDate.of(2026, 4, 28), LocalTime.of(14, 0), LocalTime.of(16, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(prenotazioneRepository.existsActiveOverlapForUser(
                11L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(locationServiceClient.getStanza(3L, "Bearer token"))
                .thenReturn(new ExternalStanzaResponse(3L, "Sala Atlante", "MEETING_ROOM", "meeting-1", 5L, 2));
        when(prenotazioneRepository.existsActiveOverlapForMeetingRoom(
                3L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(prenotazioneRepository.saveAndFlush(any(Prenotazione.class))).thenAnswer(invocation -> {
            Prenotazione prenotazione = invocation.getArgument(0);
            prenotazione.setId(102L);
            prenotazione.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            prenotazione.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return prenotazione;
        });

        var response = prenotazioneService.createMeetingRoom(request, "Bearer token");

        assertThat(response.getId()).isEqualTo(102L);
        assertThat(response.getTipoRisorsaPrenotata()).isEqualTo(TipoRisorsaPrenotata.MEETING_ROOM);
        assertThat(response.getPostazioneId()).isNull();
        assertThat(response.getMeetingRoomStanzaId()).isEqualTo(3L);
        assertThat(response.getRisorsaLabel()).isEqualTo("Sala Atlante");
        verify(prenotazioneEventPublisher).pubblicaConferma(response);
    }

    @Test
    void createMeetingRoom_rifiutaRuoloNonAbilitato() {
        CreatePrenotazioneRequest request = buildMeetingRoomRequest(3L, LocalDate.of(2026, 4, 28), LocalTime.of(14, 0), LocalTime.of(16, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.GUEST));

        assertThatThrownBy(() -> prenotazioneService.createMeetingRoom(request, "Bearer token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).contains("non puo' creare prenotazioni");
                });
    }

    @Test
    void createMeetingRoom_rifiutaRichiestaSalaIncoerente() {
        CreatePrenotazioneRequest request = new CreatePrenotazioneRequest(7L, null, LocalDate.of(2026, 4, 28), LocalTime.of(14, 0), LocalTime.of(16, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));

        assertThatThrownBy(() -> prenotazioneService.createMeetingRoom(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("una sola sala riunioni");
    }

    @Test
    void createMeetingRoom_rifiutaStanzaCheNonEUnaSalaRiunioni() {
        CreatePrenotazioneRequest request = buildMeetingRoomRequest(3L, LocalDate.of(2026, 4, 28), LocalTime.of(14, 0), LocalTime.of(16, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(locationServiceClient.getStanza(3L, "Bearer token"))
                .thenReturn(new ExternalStanzaResponse(3L, "Open Space", "ROOM", "room-1", 5L, 8));

        assertThatThrownBy(() -> prenotazioneService.createMeetingRoom(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non e' una sala riunioni prenotabile");
    }

    @Test
    void createMeetingRoom_rifiutaSalaGiaOccupata() {
        CreatePrenotazioneRequest request = buildMeetingRoomRequest(3L, LocalDate.of(2026, 4, 28), LocalTime.of(14, 0), LocalTime.of(16, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(prenotazioneRepository.existsActiveOverlapForUser(
                11L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(locationServiceClient.getStanza(3L, "Bearer token"))
                .thenReturn(new ExternalStanzaResponse(3L, "Sala Atlante", "MEETING_ROOM", "meeting-1", 5L, 8));
        when(prenotazioneRepository.existsActiveOverlapForMeetingRoom(
                3L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(true);

        assertThatThrownBy(() -> prenotazioneService.createMeetingRoom(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sala riunioni e' gia' prenotata");
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
        when(prenotazioneRepository.existsActiveOverlapForUserExcludingId(
                22L,
                11L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(prenotazioneRepository.existsActiveOverlapForPostazioneExcludingId(
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
        when(prenotazioneRepository.existsActiveOverlapForUserExcludingId(
                22L,
                11L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(prenotazioneRepository.existsActiveOverlapForPostazioneExcludingId(
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
    void update_rifiutaPrenotazioneFuoriDallaFasciaNoveDiciotto() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 29), LocalTime.of(9, 0), LocalTime.of(13, 0));
        UpdatePrenotazioneRequest request = new UpdatePrenotazioneRequest(
                LocalDate.of(2026, 4, 30),
                LocalTime.of(10, 0),
                LocalTime.of(19, 0)
        );

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));

        assertThatThrownBy(() -> prenotazioneService.update(22L, request, "Bearer token", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("09:00 e le 18:00");
    }

    @Test
    void update_rifiutaModificaDiPrenotazioneAltrui() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 29), LocalTime.of(9, 0), LocalTime.of(13, 0));
        UpdatePrenotazioneRequest request = new UpdatePrenotazioneRequest(
                LocalDate.of(2026, 4, 30),
                LocalTime.of(10, 0),
                LocalTime.of(17, 0)
        );

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(99L, RuoloUtente.USER));

        assertThatThrownBy(() -> prenotazioneService.update(22L, request, "Bearer token", false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).contains("solo le tue prenotazioni");
                });
    }

    @Test
    void update_consenteGestioneAdminAncheSeNonProprietario() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 29), LocalTime.of(9, 0), LocalTime.of(13, 0));
        UpdatePrenotazioneRequest request = new UpdatePrenotazioneRequest(
                LocalDate.of(2026, 4, 30),
                LocalTime.of(10, 0),
                LocalTime.of(17, 0)
        );

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(prenotazioneRepository.existsActiveOverlapForUserExcludingId(
                22L,
                11L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(prenotazioneRepository.existsActiveOverlapForPostazioneExcludingId(
                22L,
                7L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(prenotazioneRepository.saveAndFlush(prenotazione)).thenReturn(prenotazione);

        var response = prenotazioneService.update(22L, request, "Bearer token", true);

        assertThat(response.getDataPrenotazione()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(response.getOraInizio()).isEqualTo(LocalTime.of(10, 0));
        assertThat(response.getOraFine()).isEqualTo(LocalTime.of(17, 0));
        verify(utentiServiceClient, never()).getCurrentUser("Bearer token");
    }

    @Test
    void update_rifiutaPrenotazioneDelGiornoCorrente() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 27), LocalTime.of(14, 0), LocalTime.of(18, 0));
        UpdatePrenotazioneRequest request = new UpdatePrenotazioneRequest(
                LocalDate.of(2026, 4, 30),
                LocalTime.of(10, 0),
                LocalTime.of(17, 0)
        );

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));

        assertThatThrownBy(() -> prenotazioneService.update(22L, request, "Bearer token", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("solo prenotazioni future");
    }

    @Test
    void update_rifiutaPrenotazioneNonConfermata() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 29), LocalTime.of(9, 0), LocalTime.of(13, 0));
        prenotazione.setStato(StatoPrenotazione.ANNULLATA);
        UpdatePrenotazioneRequest request = new UpdatePrenotazioneRequest(
                LocalDate.of(2026, 4, 30),
                LocalTime.of(10, 0),
                LocalTime.of(17, 0)
        );

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));

        assertThatThrownBy(() -> prenotazioneService.update(22L, request, "Bearer token", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("solo prenotazioni confermate");
    }

    @Test
    void update_rifiutaOverlapSuSalaRiunioni() {
        Prenotazione prenotazione = buildMeetingRoomPrenotazione(30L, 11L, 3L, LocalDate.of(2026, 4, 29), LocalTime.of(9, 0), LocalTime.of(13, 0));
        UpdatePrenotazioneRequest request = new UpdatePrenotazioneRequest(
                LocalDate.of(2026, 4, 29),
                LocalTime.of(10, 0),
                LocalTime.of(17, 0)
        );

        when(prenotazioneRepository.findById(30L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(prenotazioneRepository.existsActiveOverlapForUserExcludingId(
                30L,
                11L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(prenotazioneRepository.existsActiveOverlapForMeetingRoomExcludingId(
                30L,
                3L,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(true);

        assertThatThrownBy(() -> prenotazioneService.update(30L, request, "Bearer token", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sala riunioni e' gia' prenotata");
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

    @Test
    void annulla_rifiutaPrenotazioneGiaAnnullata() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));
        prenotazione.setStato(StatoPrenotazione.ANNULLATA);

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));

        assertThatThrownBy(() -> prenotazioneService.annulla(22L, "Bearer token", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gia' annullata");
    }

    @Test
    void annulla_rifiutaPrenotazioneAltrui() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(99L, RuoloUtente.USER));

        assertThatThrownBy(() -> prenotazioneService.annulla(22L, "Bearer token", false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).contains("solo le tue prenotazioni");
                });
    }

    @Test
    void annulla_rifiutaPrenotazioneInCorso() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 27), LocalTime.of(9, 0), LocalTime.of(13, 0));

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));

        assertThatThrownBy(() -> prenotazioneService.annulla(22L, "Bearer token", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gia' in corso");
    }

    @Test
    void annulla_rifiutaPrenotazioneConclusa() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 27), LocalTime.of(8, 0), LocalTime.of(9, 0));

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));

        assertThatThrownBy(() -> prenotazioneService.annulla(22L, "Bearer token", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gia' conclusa");
    }

    @Test
    void annulla_consenteGestioneAdminAncheSeNonProprietario() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));

        prenotazioneService.annulla(22L, "Bearer token", true);

        verify(prenotazioneRepository).delete(prenotazione);
        verify(utentiServiceClient, never()).getCurrentUser("Bearer token");
    }

    @Test
    void findById_rifiutaPrenotazioneAltrui() {
        Prenotazione prenotazione = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(99L, RuoloUtente.USER));

        assertThatThrownBy(() -> prenotazioneService.findById(22L, "Bearer token", false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).contains("solo le tue prenotazioni");
                });
    }

    @Test
    void isDisponibile_restituisceFalseQuandoEsisteOverlap() {
        when(prenotazioneRepository.existsActiveOverlapForPostazione(
                7L,
                LocalDate.of(2026, 4, 28),
                LocalTime.of(9, 0),
                LocalTime.of(13, 0),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(true);

        boolean disponibile = prenotazioneService.isDisponibile(7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));

        assertThat(disponibile).isFalse();
    }

    @Test
    void isDisponibile_rifiutaSlotNonValido() {
        assertThatThrownBy(() -> prenotazioneService.isDisponibile(7L, LocalDate.of(2026, 4, 27), LocalTime.of(9, 0), LocalTime.of(13, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("giorno successivo");
    }

    @Test
    void isMeetingRoomDisponibile_restituisceFalseQuandoEsisteOverlap() {
        when(prenotazioneRepository.existsActiveOverlapForMeetingRoom(
                3L,
                LocalDate.of(2026, 4, 28),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(true);

        boolean disponibile = prenotazioneService.isMeetingRoomDisponibile(3L, LocalDate.of(2026, 4, 28), LocalTime.of(14, 0), LocalTime.of(16, 0));

        assertThat(disponibile).isFalse();
    }

    @Test
    void annullaPrenotazioniFuturePerUtenteEliminato_saltaQuelleGiaConcluseEdEliminaLeAltre() {
        Prenotazione conclusa = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 27), LocalTime.of(8, 0), LocalTime.of(9, 0));
        Prenotazione futura = buildPrenotazione(23L, 11L, 8L, LocalDate.of(2026, 4, 28), LocalTime.of(14, 0), LocalTime.of(18, 0));

        when(prenotazioneRepository.findByUtenteIdAndStatoAndDataPrenotazioneGreaterThanEqual(
                11L,
                StatoPrenotazione.CONFERMATA,
                LocalDate.of(2026, 4, 27)
        )).thenReturn(List.of(conclusa, futura));

        prenotazioneService.annullaPrenotazioniFuturePerUtenteEliminato(11L);

        verify(prenotazioneRepository, never()).delete(conclusa);
        verify(prenotazioneRepository).delete(futura);
        verify(prenotazioneEventPublisher, times(1)).pubblicaAnnullamento(any());
    }

    @Test
    void eliminaPrenotazioniPerPlanimetria_ignoraListeVuote() {
        prenotazioneService.eliminaPrenotazioniPerPlanimetria(List.of());

        verify(prenotazioneRepository, never()).findByPostazioneIdIn(any());
        verify(prenotazioneRepository, never()).delete(any(Prenotazione.class));
    }

    @Test
    void eliminaPrenotazioniPerPlanimetria_eliminaTutteLePrenotazioniCoinvolte() {
        Prenotazione prima = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));
        Prenotazione seconda = buildPrenotazione(23L, 12L, 8L, LocalDate.of(2026, 4, 29), LocalTime.of(14, 0), LocalTime.of(18, 0));

        when(prenotazioneRepository.findByPostazioneIdIn(List.of(7L, 8L))).thenReturn(List.of(prima, seconda));

        prenotazioneService.eliminaPrenotazioniPerPlanimetria(List.of(7L, 8L));

        verify(prenotazioneRepository).delete(prima);
        verify(prenotazioneRepository).delete(seconda);
        verify(prenotazioneEventPublisher, times(2)).pubblicaAnnullamento(any());
    }

    @Test
    void findMineForDashboard_arricchisceLePrenotazioniESfruttaCachePerLaStessaStanza() {
        Prenotazione prima = buildPrenotazione(22L, 11L, 7L, LocalDate.of(2026, 4, 29), LocalTime.of(9, 0), LocalTime.of(13, 0));
        Prenotazione seconda = buildPrenotazione(23L, 11L, 8L, LocalDate.of(2026, 4, 30), LocalTime.of(14, 0), LocalTime.of(18, 0));
        seconda.setStanzaId(3L);
        seconda.setPostazioneCodice("PS-008");

        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(prenotazioneRepository.findByUtenteIdOrderByDataPrenotazioneDescOraInizioAsc(11L))
                .thenReturn(List.of(prima, seconda));
        when(locationServiceClient.getStanza(3L, "Bearer token"))
                .thenReturn(new ExternalStanzaResponse(3L, "Open Space", "ROOM", "room-1", 5L, 2));
        when(locationServiceClient.getPiano(5L, "Bearer token"))
                .thenReturn(new it.exprivia.prenotazioni.dto.ExternalPianoResponse(5L, 2, "", 9L, "Edificio A"));
        when(locationServiceClient.getEdificio(9L, "Bearer token"))
                .thenReturn(new it.exprivia.prenotazioni.dto.ExternalEdificioResponse(9L, "Edificio A", 12L, "Sede Milano"));
        when(locationServiceClient.getSede(12L, "Bearer token"))
                .thenReturn(new it.exprivia.prenotazioni.dto.ExternalSedeResponse(12L, "HQ", "Via Roma 1", "Milano"));

        var response = prenotazioneService.findMineForDashboard("Bearer token", null);

        assertThat(response).hasSize(2);
        assertThat(response)
                .extracting(item -> item.getSedeLabel() + " | " + item.getPianoLabel())
                .containsOnly("HQ - Milano | Secondo piano");
        verify(locationServiceClient, times(1)).getStanza(3L, "Bearer token");
        verify(locationServiceClient, times(1)).getPiano(5L, "Bearer token");
        verify(locationServiceClient, times(1)).getEdificio(9L, "Bearer token");
        verify(locationServiceClient, times(1)).getSede(12L, "Bearer token");
    }

    @Test
    void findByResources_redigeDatiUtentePerRuoliNonOperativi() {
        Prenotazione prenotazione = buildPrenotazione(44L, 11L, 7L, LocalDate.of(2026, 4, 28), LocalTime.of(9, 0), LocalTime.of(13, 0));
        when(prenotazioneRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(prenotazione));

        var response = prenotazioneService.findByResources(
                LocalDate.of(2026, 4, 28),
                List.of(7L),
                List.of(),
                false
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getUtenteId()).isNull();
        assertThat(response.get(0).getUtenteEmail()).isNull();
        assertThat(response.get(0).getUtenteFullName()).isNull();
        assertThat(response.get(0).getPostazioneId()).isEqualTo(7L);
    }

    @Test
    void findByResources_richiedeAlmenoUnaRisorsa() {
        assertThatThrownBy(() -> prenotazioneService.findByResources(
                LocalDate.of(2026, 4, 28),
                List.of(),
                List.of(),
                false
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getReason()).contains("Specifica almeno una postazione");
        });

        verify(prenotazioneRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    private CreatePrenotazioneRequest buildCreateRequest(Long postazioneId, LocalDate data, LocalTime oraInizio, LocalTime oraFine) {
        return new CreatePrenotazioneRequest(postazioneId, null, data, oraInizio, oraFine);
    }

    private CreatePrenotazioneRequest buildMeetingRoomRequest(Long stanzaId, LocalDate data, LocalTime oraInizio, LocalTime oraFine) {
        return new CreatePrenotazioneRequest(null, stanzaId, data, oraInizio, oraFine);
    }

    private void stubCreatePostazioneFlow(CreatePrenotazioneRequest request, Long utenteId) {
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(utenteId, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(request.getPostazioneId(), "Bearer token"))
                .thenReturn(buildPostazione(request.getPostazioneId(), "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(request.getPostazioneId(), "Bearer token")).thenReturn(List.of());
        when(utentiServiceClient.getCurrentUserGroupIds("Bearer token")).thenReturn(List.of());
        when(prenotazioneRepository.existsActiveOverlapForUser(
                utenteId,
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
        when(prenotazioneRepository.existsActiveOverlapForPostazione(
                request.getPostazioneId(),
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )).thenReturn(false);
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
        prenotazione.setTipoRisorsaPrenotata(TipoRisorsaPrenotata.POSTAZIONE);
        prenotazione.setPostazioneId(postazioneId);
        prenotazione.setPostazioneCodice("PS-007");
        prenotazione.setMeetingRoomStanzaId(null);
        prenotazione.setMeetingRoomNome(null);
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

    private Prenotazione buildMeetingRoomPrenotazione(Long id,
                                                      Long utenteId,
                                                      Long stanzaId,
                                                      LocalDate dataPrenotazione,
                                                      LocalTime oraInizio,
                                                      LocalTime oraFine) {
        Prenotazione prenotazione = buildPrenotazione(id, utenteId, null, dataPrenotazione, oraInizio, oraFine);
        prenotazione.setTipoRisorsaPrenotata(TipoRisorsaPrenotata.MEETING_ROOM);
        prenotazione.setPostazioneCodice(null);
        prenotazione.setMeetingRoomStanzaId(stanzaId);
        prenotazione.setMeetingRoomNome("Sala Atlante");
        prenotazione.setStanzaId(stanzaId);
        prenotazione.setStanzaNome("Sala Atlante");
        return prenotazione;
    }
}
