package it.exprivia.location.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione RabbitMQ per il microservizio location.
 *
 * Questo servizio è CONSUMER (riceve messaggi), non publisher.
 * Dichiara le code e i binding necessari per ricevere gli eventi
 * pubblicati da utenti-service sull'exchange "utenti.events".
 *
 * Architettura messaggi:
 * - utenti-service pubblica su EXCHANGE con routing key specifica
 * - Questo servizio dichiara le sue QUEUE e le collega all'exchange
 *   tramite BINDING con la routing key corrispondente
 * - durable(true) = la coda sopravvive a riavvii di RabbitMQ
 */
@Configuration
public class RabbitMQConfig {

    // Exchange su cui utenti-service pubblica i suoi eventi (deve coincidere con quello di utenti-service)
    public static final String EXCHANGE = "utenti.events";
    public static final String EXCHANGE_LOCATION = "location.events";

    // Coda di questo servizio per ricevere eventi di eliminazione gruppo
    public static final String QUEUE = "location.gruppo.eliminato";

    // Routing key usata da utenti-service per gli eventi di eliminazione gruppo
    public static final String ROUTING_KEY_GRUPPO_ELIMINATO = "gruppo.eliminato";

    // Coda di questo servizio per ricevere eventi di eliminazione utente
    public static final String QUEUE_UTENTE = "location.utente.eliminato";

    // Routing key usata da utenti-service per gli eventi di eliminazione utente
    public static final String ROUTING_KEY_UTENTE_ELIMINATO = "utente.eliminato";
    public static final String ROUTING_KEY_PLANIMETRIA_ELIMINATA = "planimetria.eliminata";
    public static final String ROUTING_KEY_POSTAZIONE_NON_PRENOTABILE = "postazione.non.prenotabile";
    public static final String ROUTING_KEY_MEETING_ROOM_NON_PRENOTABILE = "meeting-room.non.prenotabile";

    /**
     * Dichiara l'exchange su cui ascoltiamo (TopicExchange per pattern matching nelle routing key).
     */
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public TopicExchange locationExchange() {
        return new TopicExchange(EXCHANGE_LOCATION);
    }

    /**
     * Dichiara la coda per gli eventi di eliminazione gruppo.
     * durable = la coda persiste su disco, non si perde al riavvio di RabbitMQ.
     */
    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    /**
     * Collega la coda gruppo all'exchange con la routing key "gruppo.eliminato".
     * Così solo i messaggi con quella routing key arrivano su questa coda.
     */
    @Bean
    public Binding binding(@Qualifier("queue") Queue queue,
                           @Qualifier("exchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_GRUPPO_ELIMINATO);
    }

    /**
     * Dichiara la coda per gli eventi di eliminazione utente.
     */
    @Bean
    public Queue queueUtente() {
        return QueueBuilder.durable(QUEUE_UTENTE).build();
    }

    /**
     * Collega la coda utente all'exchange con la routing key "utente.eliminato".
     */
    @Bean
    public Binding bindingUtente(@Qualifier("queueUtente") Queue queueUtente,
                                 @Qualifier("exchange") TopicExchange exchange) {
        return BindingBuilder.bind(queueUtente).to(exchange).with(ROUTING_KEY_UTENTE_ELIMINATO);
    }

    /**
     * Configura la conversione automatica JSON ↔ oggetto Java per i messaggi.
     */
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Configura il RabbitTemplate con il convertitore JSON.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
