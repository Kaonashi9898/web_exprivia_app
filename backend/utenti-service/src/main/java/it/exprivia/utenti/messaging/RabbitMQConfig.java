package it.exprivia.utenti.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configurazione di RabbitMQ per il microservizio utenti.
 *
 * Definisce le costanti usate per l'instradamento dei messaggi e configura
 * il convertitore JSON in modo che i messaggi vengano serializzati/deserializzati
 * automaticamente come oggetti Java.
 *
 * Architettura messaggi:
 * - L'EXCHANGE è un "topic exchange": riceve i messaggi e li smista alle code
 *   in base alla routing key
 * - Le ROUTING KEY identificano il tipo di evento (es. "gruppo.eliminato")
 * - I consumer (altri microservizi) si collegano all'exchange con le routing key
 *   degli eventi a cui sono interessati
 */
@Configuration
@EnableAsync
public class RabbitMQConfig {

    // Nome dell'exchange su cui questo servizio pubblica tutti gli eventi
    public static final String EXCHANGE = "utenti.events";
    public static final String ROUTING_KEY_GRUPPO_CREATO = "gruppo.creato";
    public static final String ROUTING_KEY_GRUPPO_ACCESSO = "gruppo.accesso";
    public static final String ROUTING_KEY_GRUPPO_AGGIORNATO = "gruppo.aggiornato";
    public static final String ROUTING_KEY_GRUPPO_UTENTE_AGGIUNTO = "gruppo.utente.aggiunto";
    public static final String ROUTING_KEY_GRUPPO_UTENTE_RIMOSSO = "gruppo.utente.rimosso";
    public static final String ROUTING_KEY_UTENTE_CREATO = "utente.creato";
    public static final String ROUTING_KEY_UTENTE_ACCESSO = "utente.accesso";
    public static final String ROUTING_KEY_UTENTE_AGGIORNATO = "utente.aggiornato";

    // Routing key per l'evento "un gruppo è stato eliminato"
    // Usata da location-service per pulire le postazioni assegnate al gruppo
    public static final String ROUTING_KEY_GRUPPO_ELIMINATO = "gruppo.eliminato";

    // Routing key per l'evento "un utente è stato eliminato"
    // Usata da location-service per rimuovere le assegnazioni dell'utente
    public static final String ROUTING_KEY_UTENTE_ELIMINATO = "utente.eliminato";

    @Bean
    public TopicExchange utentiExchange() {
        return new TopicExchange(EXCHANGE);
    }

    /**
     * Configura il convertitore di messaggi Jackson.
     * Grazie a questo, gli oggetti Java vengono automaticamente convertiti
     * in JSON quando vengono inviati su RabbitMQ e viceversa.
     */
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Configura il RabbitTemplate con il convertitore JSON.
     * Il RabbitTemplate è il componente Spring usato per inviare messaggi a RabbitMQ.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean(name = "rabbitEventExecutor")
    public Executor rabbitEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("rabbit-event-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
