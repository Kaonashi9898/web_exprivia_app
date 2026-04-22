package it.exprivia.utenti.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

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
public class GruppoEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Pubblica un evento di eliminazione utente su RabbitMQ.
     * I consumer interessati (es. location-service) riceveranno il messaggio
     * e rimuoveranno i dati relativi a quell'utente.
     *
     * @param event l'evento con ID utente e lista dei gruppi a cui apparteneva
     */
    public void pubblicaEliminazioneUtente(UtenteEliminatoEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,                          // exchange di destinazione
                RabbitMQConfig.ROUTING_KEY_UTENTE_ELIMINATO,      // chiave di instradamento
                event                                             // payload serializzato in JSON
        );
    }

    /**
     * Pubblica un evento di eliminazione gruppo su RabbitMQ.
     *
     * @param gruppoId l'ID del gruppo appena eliminato
     */
    public void pubblicaEliminazione(Long gruppoId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,                          // exchange di destinazione
                RabbitMQConfig.ROUTING_KEY_GRUPPO_ELIMINATO,      // chiave di instradamento
                new GruppoEliminatoEvent(gruppoId)                // payload serializzato in JSON
        );
    }
}
