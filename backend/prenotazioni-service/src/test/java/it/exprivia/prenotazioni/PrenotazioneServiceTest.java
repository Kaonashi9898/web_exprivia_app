package it.exprivia.prenotazioni;

import it.exprivia.prenotazioni.messaging.PrenotazioneEventPublisher;
import it.exprivia.prenotazioni.repository.PrenotazioneRepository;
import it.exprivia.prenotazioni.service.LocationServiceClient;
import it.exprivia.prenotazioni.service.PrenotazioneService;
import it.exprivia.prenotazioni.service.UtentiServiceClient;
import it.exprivia.prenotazioni.dto.CreatePrenotazioneRequest;
import it.exprivia.prenotazioni.dto.ExternalPostazioneResponse;
import it.exprivia.prenotazioni.dto.ExternalUtenteResponse;
import it.exprivia.prenotazioni.entity.Prenotazione;
import it.exprivia.prenotazioni.entity.RuoloUtente;
import it.exprivia.prenotazioni.entity.StatoPrenotazione;

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
            Instant.parse("2026-04-16T08:00:00Z"),
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
    void create_rifiutaSecondaPrenotazioneNelloStessoGiornoPerUtente() {
        CreatePrenotazioneRequest request = buildRequest(7L, LocalDate.of(2026, 4, 20), LocalTime.of(9, 0), LocalTime.of(13, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(10L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());
        when(prenotazioneRepository.existsByUtenteIdAndDataPrenotazioneAndStato(10L, request.getDataPrenotazione(), StatoPrenotazione.CONFERMATA))
                .thenReturn(true);

        assertThatThrownBy(() -> prenotazioneService.create(request, "Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gia' una prenotazione attiva");

        verify(prenotazioneRepository, never()).saveAndFlush(any(Prenotazione.class));
    }

    @Test
    void create_rifiutaOverlapSullaStessaPostazione() {
        CreatePrenotazioneRequest request = buildRequest(7L, LocalDate.of(2026, 4, 20), LocalTime.of(9, 0), LocalTime.of(13, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(10L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());
        when(prenotazioneRepository.existsByUtenteIdAndDataPrenotazioneAndStato(10L, request.getDataPrenotazione(), StatoPrenotazione.CONFERMATA))
                .thenReturn(false);
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
        CreatePrenotazioneRequest request = buildRequest(7L, LocalDate.of(2026, 4, 20), LocalTime.of(13, 0), LocalTime.of(18, 0));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(locationServiceClient.getPostazione(7L, "Bearer token")).thenReturn(buildPostazione(7L, "PS-007", "DISPONIBILE"));
        when(locationServiceClient.getGruppiAbilitati(7L, "Bearer token")).thenReturn(List.of());
        when(prenotazioneRepository.existsByUtenteIdAndDataPrenotazioneAndStato(11L, request.getDataPrenotazione(), StatoPrenotazione.CONFERMATA))
                .thenReturn(false);
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
    void annulla_prenotazioneFuturaDelProprietario() {
        Prenotazione prenotazione = new Prenotazione();
        prenotazione.setId(22L);
        prenotazione.setUtenteId(11L);
        prenotazione.setUtenteEmail("utente@exprivia.com");
        prenotazione.setUtenteFullName("Utente Test");
        prenotazione.setPostazioneId(7L);
        prenotazione.setPostazioneCodice("PS-007");
        prenotazione.setStanzaId(3L);
        prenotazione.setStanzaNome("Open Space");
        prenotazione.setDataPrenotazione(LocalDate.of(2026, 4, 17));
        prenotazione.setOraInizio(LocalTime.of(9, 0));
        prenotazione.setOraFine(LocalTime.of(13, 0));
        prenotazione.setStato(StatoPrenotazione.CONFERMATA);
        prenotazione.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        prenotazione.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(prenotazioneRepository.findById(22L)).thenReturn(Optional.of(prenotazione));
        when(utentiServiceClient.getCurrentUser("Bearer token")).thenReturn(buildUser(11L, RuoloUtente.USER));
        when(prenotazioneRepository.save(prenotazione)).thenReturn(prenotazione);

        prenotazioneService.annulla(22L, "Bearer token", false);

        assertThat(prenotazione.getStato()).isEqualTo(StatoPrenotazione.ANNULLATA);
        verify(prenotazioneEventPublisher).pubblicaAnnullamento(any());
    }

    private CreatePrenotazioneRequest buildRequest(Long postazioneId, LocalDate data, LocalTime oraInizio, LocalTime oraFine) {
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
}
