package it.exprivia.utenti.service;

import it.exprivia.utenti.dto.UtenteDTO;
import it.exprivia.utenti.entity.Gruppo;
import it.exprivia.utenti.entity.GruppoUtente;
import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
import it.exprivia.utenti.messaging.GruppoEventPublisher;
import it.exprivia.utenti.repository.GruppoRepository;
import it.exprivia.utenti.repository.GruppoUtenteRepository;
import it.exprivia.utenti.repository.UtenteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * Service che contiene la logica di business per la gestione dei gruppi.
 *
 * Si occupa di: creare gruppi, aggiungere/rimuovere utenti dai gruppi,
 * elencare gruppi e pubblicare eventi RabbitMQ quando un gruppo viene eliminato.
 */
@Service
@RequiredArgsConstructor
public class GruppoService {

    private static final Set<RuoloUtente> RUOLI_GESTIBILI_DA_RECEPTION =
            EnumSet.of(RuoloUtente.USER, RuoloUtente.GUEST);

    private final GruppoRepository gruppoRepository;
    private final GruppoUtenteRepository gruppoUtenteRepository;
    private final UtenteRepository utenteRepository;
    private final GruppoEventPublisher gruppoEventPublisher;

    /**
     * Restituisce i gruppi a cui appartiene l'utente identificato dall'email.
     * Funzionamento:
     * 1. Cerca l'utente per email
     * 2. Trova tutte le sue associazioni nella tabella gruppi_utente
     * 3. Per ogni associazione, carica il gruppo corrispondente
     */
    public List<Gruppo> getMiei(String email) {
        var utente = utenteRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con email: " + email));
        List<Long> gruppoIds = gruppoUtenteRepository.findByIdUtente(utente.getId())
                .stream()
                .map(GruppoUtente::getIdGruppo)
                .toList();
        return gruppoRepository.findAllById(gruppoIds);
    }

    /**
     * Crea un nuovo gruppo con il nome fornito e lo persiste nel database.
     */
    public Gruppo crea(String nome) {
        String normalizedNome = normalizeNome(nome);
        if (gruppoRepository.existsByNomeIgnoreCase(normalizedNome)) {
            throw new IllegalArgumentException("Esiste gia' un gruppo con questo nome");
        }

        Gruppo gruppo = new Gruppo();
        gruppo.setNome(normalizedNome);
        return gruppoRepository.save(gruppo);
    }

    /**
     * Aggiorna il nome di un gruppo esistente.
     */
    public Gruppo aggiorna(Long id, String nome) {
        Gruppo gruppo = gruppoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Gruppo non trovato con id: " + id));
        String normalizedNome = normalizeNome(nome);
        if (gruppoRepository.existsByNomeIgnoreCaseAndIdNot(normalizedNome, id)) {
            throw new IllegalArgumentException("Esiste gia' un gruppo con questo nome");
        }

        gruppo.setNome(normalizedNome);
        return gruppoRepository.save(gruppo);
    }

    /**
     * Restituisce tutti i gruppi presenti nel database.
     */
    public List<Gruppo> findAll() {
        return gruppoRepository.findAll();
    }

    /**
     * Elimina un gruppo e notifica gli altri microservizi tramite RabbitMQ.
     * L'evento viene pubblicato DOPO l'eliminazione, così gli altri servizi
     * possono liberare le risorse collegate (es. postazioni assegnate al gruppo).
     */
    public Gruppo elimina(Long id) {
        Gruppo gruppo = gruppoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Gruppo non trovato con id: " + id));
        gruppoRepository.delete(gruppo);
        // Notifica il location-service che il gruppo è stato eliminato
        gruppoEventPublisher.pubblicaEliminazione(id);
        return gruppo;
    }

    /**
     * Aggiunge un utente a un gruppo creando una nuova riga nella tabella gruppi_utente.
     * Verifica che: il gruppo esista, l'utente esista e l'utente non sia già nel gruppo.
     */
    public void aggiungiUtente(Long idGruppo, Long idUtente, String operatorEmail) {
        if (!gruppoRepository.existsById(idGruppo)) {
            throw new EntityNotFoundException("Gruppo non trovato con id: " + idGruppo);
        }
        Utente target = utenteRepository.findById(idUtente)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con id: " + idUtente));
        ensureOperatorePuoGestireTarget(getOperatore(operatorEmail), target);
        if (gruppoUtenteRepository.existsByIdGruppoAndIdUtente(idGruppo, idUtente)) {
            throw new IllegalArgumentException("L'utente è già nel gruppo");
        }

        // Crea la riga di associazione nella tabella di join
        GruppoUtente gu = new GruppoUtente();
        gu.setIdGruppo(idGruppo);
        gu.setIdUtente(idUtente);
        gruppoUtenteRepository.save(gu);
    }

    /**
     * Rimuove un utente da un gruppo eliminando la riga corrispondente
     * nella tabella di join gruppi_utente.
     */
    public void rimuoviUtente(Long idGruppo, Long idUtente, String operatorEmail) {
        Utente target = utenteRepository.findById(idUtente)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato con id: " + idUtente));
        ensureOperatorePuoGestireTarget(getOperatore(operatorEmail), target);
        GruppoUtente gu = gruppoUtenteRepository.findByIdGruppoAndIdUtente(idGruppo, idUtente)
                .orElseThrow(() -> new EntityNotFoundException("L'utente non appartiene a questo gruppo"));
        gruppoUtenteRepository.delete(gu);
    }

    /**
     * Restituisce la lista degli utenti (come DTO) appartenenti a un gruppo.
     * Funzionamento:
     * 1. Controlla che il gruppo esista
     * 2. Carica tutte le associazioni del gruppo
     * 3. Per ogni associazione, recupera l'utente e lo converte in DTO
     */
    public List<UtenteDTO> getUtentiDelGruppo(Long idGruppo) {
        if (!gruppoRepository.existsById(idGruppo)) {
            throw new EntityNotFoundException("Gruppo non trovato con id: " + idGruppo);
        }

        List<Long> utenteIds = gruppoUtenteRepository.findByIdGruppo(idGruppo)
                .stream()
                .map(GruppoUtente::getIdUtente)
                .toList();
        return utenteRepository.findAllById(utenteIds)
                .stream()
                .map(u -> new UtenteDTO(u.getId(), u.getFullName(), u.getEmail(), u.getRuolo()))
                .toList();
    }

    private Utente getOperatore(String operatorEmail) {
        String normalizedEmail = operatorEmail == null ? null : operatorEmail.trim().toLowerCase(Locale.ROOT);
        return utenteRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Operatore non trovato con email: " + normalizedEmail));
    }

    private void ensureOperatorePuoGestireTarget(Utente operatore, Utente target) {
        if (operatore.getRuolo() == RuoloUtente.ADMIN) {
            return;
        }
        if (operatore.getRuolo() != RuoloUtente.RECEPTION) {
            throw new ResponseStatusException(FORBIDDEN, "Permessi insufficienti per gestire i gruppi utente");
        }
        if (!RUOLI_GESTIBILI_DA_RECEPTION.contains(target.getRuolo())) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "RECEPTION puo' gestire gruppi solo per utenti con ruolo USER o GUEST"
            );
        }
    }

    private String normalizeNome(String nome) {
        String normalized = nome == null ? "" : nome.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Il nome del gruppo e' obbligatorio");
        }
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("Il nome del gruppo non puo' superare i 50 caratteri");
        }
        return normalized;
    }
}
