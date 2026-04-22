package it.exprivia.prenotazioni.config;

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


@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_PRENOTAZIONI = "prenotazioni.events";
    public static final String EXCHANGE_UTENTI = "utenti.events";
    public static final String EXCHANGE_LOCATION = "location.events";
    public static final String ROUTING_KEY_PRENOTAZIONE_CONFERMATA = "prenotazione.confermata";
    public static final String ROUTING_KEY_PRENOTAZIONE_ANNULLATA = "prenotazione.annullata";
    public static final String ROUTING_KEY_UTENTE_ELIMINATO = "utente.eliminato";
    public static final String ROUTING_KEY_PLANIMETRIA_ELIMINATA = "planimetria.eliminata";
    public static final String QUEUE_UTENTE_ELIMINATO = "prenotazioni.utente.eliminato";
    public static final String QUEUE_PLANIMETRIA_ELIMINATA = "prenotazioni.planimetria.eliminata";

    @Bean
    public TopicExchange prenotazioniExchange() {
        return new TopicExchange(EXCHANGE_PRENOTAZIONI);
    }

    @Bean
    public TopicExchange utentiExchange() {
        return new TopicExchange(EXCHANGE_UTENTI);
    }

    @Bean
    public TopicExchange locationExchange() {
        return new TopicExchange(EXCHANGE_LOCATION);
    }

    @Bean
    public Queue queueUtenteEliminato() {
        return QueueBuilder.durable(QUEUE_UTENTE_ELIMINATO).build();
    }

    @Bean
    public Queue queuePlanimetriaEliminata() {
        return QueueBuilder.durable(QUEUE_PLANIMETRIA_ELIMINATA).build();
    }

    @Bean
    public Binding bindingUtenteEliminato(@Qualifier("queueUtenteEliminato") Queue queueUtenteEliminato,
                                          @Qualifier("utentiExchange") TopicExchange utentiExchange) {
        return BindingBuilder.bind(queueUtenteEliminato).to(utentiExchange).with(ROUTING_KEY_UTENTE_ELIMINATO);
    }

    @Bean
    public Binding bindingPlanimetriaEliminata(@Qualifier("queuePlanimetriaEliminata") Queue queuePlanimetriaEliminata,
                                               @Qualifier("locationExchange") TopicExchange locationExchange) {
        return BindingBuilder.bind(queuePlanimetriaEliminata).to(locationExchange).with(ROUTING_KEY_PLANIMETRIA_ELIMINATA);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
