package it.exprivia.prenotazioni.service;

import it.exprivia.prenotazioni.dto.CreatePrenotazioneRequest;
import it.exprivia.prenotazioni.dto.DashboardPrenotazioneResponse;
import it.exprivia.prenotazioni.dto.ExternalEdificioResponse;
import it.exprivia.prenotazioni.dto.ExternalPianoResponse;
import it.exprivia.prenotazioni.dto.ExternalPostazioneResponse;
import it.exprivia.prenotazioni.dto.ExternalSedeResponse;
import it.exprivia.prenotazioni.dto.ExternalStanzaResponse;
import it.exprivia.prenotazioni.dto.ExternalUtenteResponse;
import it.exprivia.prenotazioni.dto.PrenotazioneResponse;
import it.exprivia.prenotazioni.dto.UpdatePrenotazioneRequest;
import it.exprivia.prenotazioni.entity.RuoloUtente;
import it.exprivia.prenotazioni.entity.Prenotazione;
import it.exprivia.prenotazioni.entity.StatoPrenotazione;
import it.exprivia.prenotazioni.messaging.PrenotazioneEventPublisher;
import it.exprivia.prenotazioni.repository.PrenotazioneRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrenotazioneService {

    private static final String STATO_POSTAZIONE_DISPONIBILE = "DISPONIBILE";
    private static final Set<RuoloUtente> RUOLI_ABILITATI_PRENOTAZIONE =
            EnumSet.of(RuoloUtente.USER, RuoloUtente.BUILDING_MANAGER, RuoloUtente.RECEPTION, RuoloUtente.ADMIN);

    private final PrenotazioneRepository prenotazioneRepository;
    private final UtentiServiceClient utentiServiceClient;
    private final LocationServiceClient locationServiceClient;
    private final PrenotazioneEventPublisher prenotazioneEventPublisher;
    private final Clock clock;

    @Transactional
    public PrenotazioneResponse create(CreatePrenotazioneRequest request, String authorizationHeader) {
        ExternalUtenteResponse utente = utentiServiceClient.getCurrentUser(authorizationHeader);
        ensureRuoloPuoPrenotare(utente);

        ExternalPostazioneResponse postazione = locationServiceClient.getPostazione(request.getPostazioneId(), authorizationHeader);
        ensurePostazionePrenotabile(postazione);
        ensureAccessoGruppoConsentito(authorizationHeader, request.getPostazioneId());
        validateBookableSlot(request.getDataPrenotazione(), request.getOraInizio(), request.getOraFine());

        prenotazioneRepository.findFirstByUtenteIdAndDataPrenotazioneAndStatoOrderByOraInizioAsc(
                utente.id(),
                request.getDataPrenotazione(),
                StatoPrenotazione.CONFERMATA
        ).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Non puoi prenotare: hai gia' una prenotazione per il giorno selezionato dalle "
                            + existing.getOraInizio()
                            + " alle "
                            + existing.getOraFine()
                            + " per la postazione "
                            + existing.getPostazioneCodice()
                            + " nella stanza "
                            + existing.getStanzaNome()
            );
        });

        if (prenotazioneRepository.existsActiveOverlap(
                request.getPostazioneId(),
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )) {
            throw new IllegalArgumentException("La postazione e' gia' prenotata nella fascia oraria richiesta");
        }

        Prenotazione prenotazione = new Prenotazione();
        prenotazione.setUtenteId(utente.id());
        prenotazione.setUtenteEmail(utente.email());
        prenotazione.setUtenteFullName(utente.fullName());
        prenotazione.setPostazioneId(postazione.id());
        prenotazione.setPostazioneCodice(postazione.codice());
        prenotazione.setStanzaId(postazione.stanzaId());
        prenotazione.setStanzaNome(postazione.stanzaNome());
        prenotazione.setDataPrenotazione(request.getDataPrenotazione());
        prenotazione.setOraInizio(request.getOraInizio());
        prenotazione.setOraFine(request.getOraFine());
        prenotazione.setStato(StatoPrenotazione.CONFERMATA);

        try {
            Prenotazione saved = prenotazioneRepository.saveAndFlush(prenotazione);
            PrenotazioneResponse response = toResponse(saved);
            prenotazioneEventPublisher.pubblicaConferma(response);
            return response;
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Conflitto di prenotazione rilevato. Aggiorna la disponibilita' e riprova.");
        }
    }

    @Transactional
    public PrenotazioneResponse update(Long id,
                                       UpdatePrenotazioneRequest request,
                                       String authorizationHeader,
                                       boolean puoGestireTutto) {
        Prenotazione prenotazione = getOrThrow(id);
        ensurePrenotazioneModificabile(prenotazione);

        if (!puoGestireTutto) {
            ExternalUtenteResponse utente = utentiServiceClient.getCurrentUser(authorizationHeader);
            if (!prenotazione.getUtenteId().equals(utente.id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Puoi modificare solo le tue prenotazioni");
            }
        }

        validateBookableSlot(request.getDataPrenotazione(), request.getOraInizio(), request.getOraFine());

        prenotazioneRepository.findFirstByUtenteIdAndDataPrenotazioneAndStatoAndIdNotOrderByOraInizioAsc(
                prenotazione.getUtenteId(),
                request.getDataPrenotazione(),
                StatoPrenotazione.CONFERMATA,
                prenotazione.getId()
        ).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Non puoi spostare la prenotazione: hai gia' una prenotazione per il giorno selezionato dalle "
                            + existing.getOraInizio()
                            + " alle "
                            + existing.getOraFine()
                            + " per la postazione "
                            + existing.getPostazioneCodice()
                            + " nella stanza "
                            + existing.getStanzaNome()
            );
        });

        if (prenotazioneRepository.existsActiveOverlapExcludingId(
                prenotazione.getId(),
                prenotazione.getPostazioneId(),
                request.getDataPrenotazione(),
                request.getOraInizio(),
                request.getOraFine(),
                StatoPrenotazione.CONFERMATA
        )) {
            throw new IllegalArgumentException("La postazione e' gia' prenotata nella fascia oraria richiesta");
        }

        prenotazione.setDataPrenotazione(request.getDataPrenotazione());
        prenotazione.setOraInizio(request.getOraInizio());
        prenotazione.setOraFine(request.getOraFine());

        try {
            Prenotazione saved = prenotazioneRepository.saveAndFlush(prenotazione);
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Conflitto di prenotazione rilevato. Aggiorna la disponibilita' e riprova.");
        }
    }

    public List<PrenotazioneResponse> findMine(String authorizationHeader, LocalDate dataPrenotazione) {
        return findMineEntities(authorizationHeader, dataPrenotazione).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<DashboardPrenotazioneResponse> findMineForDashboard(String authorizationHeader, LocalDate dataPrenotazione) {
        List<Prenotazione> prenotazioni = findMineEntities(authorizationHeader, dataPrenotazione);
        Map<Long, DashboardLocationInfo> locationByStanzaId = new HashMap<>();

        return prenotazioni.stream()
                .map(prenotazione -> toDashboardResponse(
                        prenotazione,
                        locationByStanzaId.computeIfAbsent(
                                prenotazione.getStanzaId(),
                                stanzaId -> loadDashboardLocationInfo(stanzaId, authorizationHeader)
                        )
                ))
                .toList();
    }

    public PrenotazioneResponse findById(Long id, String authorizationHeader, boolean puoGestireTutto) {
        Prenotazione prenotazione = getOrThrow(id);
        if (!puoGestireTutto) {
            ExternalUtenteResponse utente = utentiServiceClient.getCurrentUser(authorizationHeader);
            if (!prenotazione.getUtenteId().equals(utente.id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Puoi vedere solo le tue prenotazioni");
            }
        }
        return toResponse(prenotazione);
    }

    public List<PrenotazioneResponse> findAll(LocalDate dataPrenotazione, Long postazioneId) {
        Specification<Prenotazione> specification = (root, query, cb) -> cb.conjunction();

        if (dataPrenotazione != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("dataPrenotazione"), dataPrenotazione));
        }
        if (postazioneId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("postazioneId"), postazioneId));
        }

        Sort sort = Sort.by(
                Sort.Order.asc("dataPrenotazione"),
                Sort.Order.asc("oraInizio")
        );

        return prenotazioneRepository.findAll(specification, sort).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PrenotazioneResponse> findByPostazioneAndData(Long postazioneId, LocalDate dataPrenotazione) {
        return prenotazioneRepository.findByPostazioneIdAndDataPrenotazioneOrderByOraInizioAsc(postazioneId, dataPrenotazione)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public boolean isDisponibile(Long postazioneId, LocalDate dataPrenotazione, LocalTime oraInizio, LocalTime oraFine) {
        validateBookableSlot(dataPrenotazione, oraInizio, oraFine);
        return !prenotazioneRepository.existsActiveOverlap(
                postazioneId,
                dataPrenotazione,
                oraInizio,
                oraFine,
                StatoPrenotazione.CONFERMATA
        );
    }

    @Transactional
    public void annulla(Long id, String authorizationHeader, boolean puoGestireTutto) {
        Prenotazione prenotazione = getOrThrow(id);
        if (prenotazione.getStato() == StatoPrenotazione.ANNULLATA) {
            throw new IllegalArgumentException("La prenotazione e' gia' annullata");
        }
        if (!puoGestireTutto) {
            ExternalUtenteResponse utente = utentiServiceClient.getCurrentUser(authorizationHeader);
            if (!prenotazione.getUtenteId().equals(utente.id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Puoi annullare solo le tue prenotazioni");
            }
        }
        if (isConclusa(prenotazione)) {
            throw new IllegalArgumentException("Non puoi annullare una prenotazione gia' conclusa");
        }

        PrenotazioneResponse response = toResponse(prenotazione);
        prenotazioneRepository.delete(prenotazione);
        prenotazioneEventPublisher.pubblicaAnnullamento(response);
    }

    @Transactional
    public void annullaPrenotazioniFuturePerUtenteEliminato(Long utenteId) {
        List<Prenotazione> prenotazioni = prenotazioneRepository.findByUtenteIdAndStatoAndDataPrenotazioneGreaterThanEqual(
                utenteId,
                StatoPrenotazione.CONFERMATA,
                LocalDate.now(clock)
        );

        for (Prenotazione prenotazione : prenotazioni) {
            if (isConclusa(prenotazione)) {
                continue;
            }
            PrenotazioneResponse response = toResponse(prenotazione);
            prenotazioneRepository.delete(prenotazione);
            prenotazioneEventPublisher.pubblicaAnnullamento(response);
        }
    }

    @Transactional
    public void eliminaPrenotazioniPerPlanimetria(List<Long> postazioneIds) {
        if (postazioneIds == null || postazioneIds.isEmpty()) {
            return;
        }

        List<Prenotazione> prenotazioni = prenotazioneRepository.findByPostazioneIdIn(postazioneIds);
        for (Prenotazione prenotazione : prenotazioni) {
            PrenotazioneResponse response = toResponse(prenotazione);
            prenotazioneRepository.delete(prenotazione);
            prenotazioneEventPublisher.pubblicaAnnullamento(response);
        }
    }

    private void ensureRuoloPuoPrenotare(ExternalUtenteResponse utente) {
        if (!RUOLI_ABILITATI_PRENOTAZIONE.contains(utente.ruolo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Il tuo ruolo non puo' creare prenotazioni");
        }
    }

    private void ensurePostazionePrenotabile(ExternalPostazioneResponse postazione) {
        if (!STATO_POSTAZIONE_DISPONIBILE.equals(postazione.stato())) {
            throw new IllegalArgumentException("La postazione non e' prenotabile nello stato attuale");
        }
    }

    private void ensureAccessoGruppoConsentito(String authorizationHeader, Long postazioneId) {
        List<Long> gruppiAbilitati = locationServiceClient.getGruppiAbilitati(postazioneId, authorizationHeader);
        if (gruppiAbilitati.isEmpty()) {
            return;
        }

        List<Long> gruppiUtente = utentiServiceClient.getCurrentUserGroupIds(authorizationHeader);
        boolean autorizzato = gruppiUtente.stream().anyMatch(gruppiAbilitati::contains);
        if (!autorizzato) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non sei autorizzato a prenotare questa postazione");
        }
    }

    private void validateTimeRange(LocalTime oraInizio, LocalTime oraFine) {
        if (!oraInizio.isBefore(oraFine)) {
            throw new IllegalArgumentException("L'ora di inizio deve essere precedente all'ora di fine");
        }
    }

    private void validateBookableSlot(LocalDate dataPrenotazione, LocalTime oraInizio, LocalTime oraFine) {
        validateTimeRange(oraInizio, oraFine);
        validateBookableDate(dataPrenotazione);
    }

    private void validateBookableDate(LocalDate dataPrenotazione) {
        LocalDate oggi = LocalDate.now(clock);
        if (!dataPrenotazione.isAfter(oggi)) {
            throw new IllegalArgumentException("Le prenotazioni sono consentite solo a partire dal giorno successivo");
        }

        DayOfWeek giorno = dataPrenotazione.getDayOfWeek();
        if (giorno == DayOfWeek.SATURDAY || giorno == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException("Le prenotazioni non sono consentite il sabato e la domenica");
        }
    }

    private void ensurePrenotazioneModificabile(Prenotazione prenotazione) {
        LocalDate oggi = LocalDate.now(clock);
        if (!prenotazione.getDataPrenotazione().isAfter(oggi)) {
            throw new IllegalArgumentException("Puoi modificare solo prenotazioni future");
        }
        if (prenotazione.getStato() != StatoPrenotazione.CONFERMATA) {
            throw new IllegalArgumentException("Puoi modificare solo prenotazioni confermate");
        }
    }

    private boolean isConclusa(Prenotazione prenotazione) {
        LocalDate oggi = LocalDate.now(clock);
        if (prenotazione.getDataPrenotazione().isBefore(oggi)) {
            return true;
        }
        if (prenotazione.getDataPrenotazione().isAfter(oggi)) {
            return false;
        }
        return !prenotazione.getOraFine().isAfter(LocalTime.now(clock));
    }

    private Prenotazione getOrThrow(Long id) {
        return prenotazioneRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prenotazione non trovata con id: " + id));
    }

    private List<Prenotazione> findMineEntities(String authorizationHeader, LocalDate dataPrenotazione) {
        ExternalUtenteResponse utente = utentiServiceClient.getCurrentUser(authorizationHeader);
        return dataPrenotazione != null
                ? prenotazioneRepository.findByUtenteIdAndDataPrenotazioneOrderByOraInizioAsc(utente.id(), dataPrenotazione)
                : prenotazioneRepository.findByUtenteIdOrderByDataPrenotazioneDescOraInizioAsc(utente.id());
    }

    private DashboardLocationInfo loadDashboardLocationInfo(Long stanzaId, String authorizationHeader) {
        ExternalStanzaResponse stanza = locationServiceClient.getStanza(stanzaId, authorizationHeader);
        ExternalPianoResponse piano = locationServiceClient.getPiano(stanza.pianoId(), authorizationHeader);
        ExternalEdificioResponse edificio = locationServiceClient.getEdificio(piano.edificioId(), authorizationHeader);
        ExternalSedeResponse sede = locationServiceClient.getSede(edificio.sedeId(), authorizationHeader);

        return new DashboardLocationInfo(
                sede.nome() + " - " + sede.citta(),
                getPianoLabel(piano.numero(), piano.nome())
        );
    }

    private String getPianoLabel(Integer numero, String nome) {
        if (nome != null && !nome.trim().isEmpty()) {
            return nome.trim();
        }
        if (numero == null) {
            return "Piano non disponibile";
        }
        if (numero == 0) {
            return "Piano terra";
        }
        if (numero == 1) {
            return "Primo piano";
        }
        if (numero == 2) {
            return "Secondo piano";
        }
        return "Piano " + numero;
    }

    private PrenotazioneResponse toResponse(Prenotazione prenotazione) {
        return new PrenotazioneResponse(
                prenotazione.getId(),
                prenotazione.getUtenteId(),
                prenotazione.getUtenteEmail(),
                prenotazione.getUtenteFullName(),
                prenotazione.getPostazioneId(),
                prenotazione.getPostazioneCodice(),
                prenotazione.getStanzaId(),
                prenotazione.getStanzaNome(),
                prenotazione.getDataPrenotazione(),
                prenotazione.getOraInizio(),
                prenotazione.getOraFine(),
                prenotazione.getStato(),
                prenotazione.getCreatedAt(),
                prenotazione.getUpdatedAt()
        );
    }

    private DashboardPrenotazioneResponse toDashboardResponse(Prenotazione prenotazione,
                                                              DashboardLocationInfo locationInfo) {
        return new DashboardPrenotazioneResponse(
                prenotazione.getId(),
                prenotazione.getUtenteId(),
                prenotazione.getUtenteEmail(),
                prenotazione.getUtenteFullName(),
                prenotazione.getPostazioneId(),
                prenotazione.getPostazioneCodice(),
                prenotazione.getStanzaId(),
                prenotazione.getStanzaNome(),
                locationInfo.sedeLabel(),
                locationInfo.pianoLabel(),
                prenotazione.getDataPrenotazione(),
                prenotazione.getOraInizio(),
                prenotazione.getOraFine(),
                prenotazione.getStato(),
                prenotazione.getCreatedAt(),
                prenotazione.getUpdatedAt()
        );
    }

    private record DashboardLocationInfo(String sedeLabel, String pianoLabel) {
    }
}
