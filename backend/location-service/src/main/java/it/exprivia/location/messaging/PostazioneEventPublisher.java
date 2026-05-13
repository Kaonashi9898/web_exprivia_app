package it.exprivia.location.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostazioneEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void pubblicaNonPrenotabile(PostazioneNonPrenotabileEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_LOCATION,
                RabbitMQConfig.ROUTING_KEY_POSTAZIONE_NON_PRENOTABILE,
                event
        );
    }
}
