package it.exprivia.utenti.messaging;

import it.exprivia.utenti.dto.UtenteDTO;
import it.exprivia.utenti.entity.Gruppo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Componente responsabile della pubblicazione di eventi su RabbitMQ.
 *
 * Usa {@link RabbitTemplate} per inviare messaggi JSON all'exchange configurato.
 * I messaggi vengono instradati ai consumer appropriati tramite routing key.
 * Il pattern "publish/subscribe" permette ai microservizi di essere disaccoppiati:
 * questo servizio non sa chi riceve il messaggio, si limita a pubblicarlo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GruppoEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Async("rabbitEventExecutor")
    public void pubblicaCreazioneUtente(UtenteDTO utente, String actorEmail) {
        pubblicaUtenteCrud(RabbitMQConfig.ROUTING_KEY_UTENTE_CREATO, "CREATE", utente, actorEmail);
    }

    @Async("rabbitEventExecutor")
    public void pubblicaAccessoUtente(UtenteDTO utente, String actorEmail) {
        pubblicaUtenteCrudSilenzioso(RabbitMQConfig.ROUTING_KEY_UTENTE_ACCESSO, "READ", utente, actorEmail);
    }

    public void pubblicaAggiornamentoUtente(UtenteDTO utente, String actorEmail) {
        pubblicaUtenteCrud(RabbitMQConfig.ROUTING_KEY_UTENTE_AGGIORNATO, "UPDATE", utente, actorEmail);
    }

    public void pubblicaCreazioneGruppo(Gruppo gruppo, String actorEmail) {
        pubblicaGruppoCrud(RabbitMQConfig.ROUTING_KEY_GRUPPO_CREATO, "CREATE", gruppo, null, actorEmail);
    }

    @Async("rabbitEventExecutor")
    public void pubblicaAccessoGruppo(Gruppo gruppo, String actorEmail) {
        pubblicaGruppoCrudSilenzioso(RabbitMQConfig.ROUTING_KEY_GRUPPO_ACCESSO, "READ", gruppo, null, actorEmail);
    }

    public void pubblicaAggiornamentoGruppo(Gruppo gruppo, String actorEmail) {
        pubblicaGruppoCrud(RabbitMQConfig.ROUTING_KEY_GRUPPO_AGGIORNATO, "UPDATE", gruppo, null, actorEmail);
    }

    public void pubblicaAggiuntaUtenteGruppo(Long gruppoId, Long utenteId, String actorEmail) {
        pubblicaGruppoCrud(
                RabbitMQConfig.ROUTING_KEY_GRUPPO_UTENTE_AGGIUNTO,
                "UPDATE_ADD_USER",
                gruppoId,
                null,
                utenteId,
                actorEmail
        );
    }

    public void pubblicaRimozioneUtenteGruppo(Long gruppoId, Long utenteId, String actorEmail) {
        pubblicaGruppoCrud(
                RabbitMQConfig.ROUTING_KEY_GRUPPO_UTENTE_RIMOSSO,
                "UPDATE_REMOVE_USER",
                gruppoId,
                null,
                utenteId,
                actorEmail
        );
    }

    /**
     * Pubblica un evento di eliminazione utente su RabbitMQ.
     * I consumer interessati (es. location-service) riceveranno il messaggio
     * e rimuoveranno i dati relativi a quell'utente.
     *
     * @param event l'evento con ID utente e lista dei gruppi a cui apparteneva
     */
    public void pubblicaEliminazioneUtente(UtenteEliminatoEvent event) {
        pubblica(RabbitMQConfig.ROUTING_KEY_UTENTE_ELIMINATO, event);
    }

    /**
     * Pubblica un evento di eliminazione gruppo su RabbitMQ.
     *
     * @param gruppoId l'ID del gruppo appena eliminato
     */
    public void pubblicaEliminazione(Long gruppoId) {
        pubblica(RabbitMQConfig.ROUTING_KEY_GRUPPO_ELIMINATO, new GruppoEliminatoEvent(gruppoId));
    }

    private void pubblicaUtenteCrud(String routingKey, String operazione, UtenteDTO utente, String actorEmail) {
        pubblica(
                routingKey,
                new UtenteCrudEvent(
                        operazione,
                        utente.getId(),
                        utente.getFullName(),
                        utente.getEmail(),
                        utente.getRuolo(),
                        actorEmail,
                        Instant.now()
                )
        );
    }

    private void pubblicaUtenteCrudSilenzioso(String routingKey, String operazione, UtenteDTO utente, String actorEmail) {
        pubblicaSilenzioso(
                routingKey,
                new UtenteCrudEvent(
                        operazione,
                        utente.getId(),
                        utente.getFullName(),
                        utente.getEmail(),
                        utente.getRuolo(),
                        actorEmail,
                        Instant.now()
                )
        );
    }

    private void pubblicaGruppoCrud(String routingKey,
                                    String operazione,
                                    Gruppo gruppo,
                                    Long utenteId,
                                    String actorEmail) {
        pubblicaGruppoCrud(routingKey, operazione, gruppo.getId(), gruppo.getNome(), utenteId, actorEmail);
    }

    private void pubblicaGruppoCrudSilenzioso(String routingKey,
                                              String operazione,
                                              Gruppo gruppo,
                                              Long utenteId,
                                              String actorEmail) {
        pubblicaGruppoCrudSilenzioso(routingKey, operazione, gruppo.getId(), gruppo.getNome(), utenteId, actorEmail);
    }

    private void pubblicaGruppoCrud(String routingKey,
                                    String operazione,
                                    Long gruppoId,
                                    String nome,
                                    Long utenteId,
                                    String actorEmail) {
        pubblica(
                routingKey,
                new GruppoCrudEvent(
                        operazione,
                        gruppoId,
                        nome,
                        utenteId,
                        actorEmail,
                        Instant.now()
                )
        );
    }

    private void pubblicaGruppoCrudSilenzioso(String routingKey,
                                              String operazione,
                                              Long gruppoId,
                                              String nome,
                                              Long utenteId,
                                              String actorEmail) {
        pubblicaSilenzioso(
                routingKey,
                new GruppoCrudEvent(
                        operazione,
                        gruppoId,
                        nome,
                        utenteId,
                        actorEmail,
                        Instant.now()
                )
        );
    }

    private void pubblica(String routingKey, Object payload) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, payload);
        } catch (RuntimeException ex) {
            log.error("Evento RabbitMQ non pubblicato su routing key {}: {}", routingKey, ex.getMessage(), ex);
            throw new EventPublicationException(
                    "Pubblicazione evento RabbitMQ fallita per routing key " + routingKey,
                    ex
            );
        }
    }

    private void pubblicaSilenzioso(String routingKey, Object payload) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, payload);
        } catch (RuntimeException ex) {
            log.warn("Evento RabbitMQ non pubblicato su routing key {}: {}", routingKey, ex.getMessage());
        }
    }
}
