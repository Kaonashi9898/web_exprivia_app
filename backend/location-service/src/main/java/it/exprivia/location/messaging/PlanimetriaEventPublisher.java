package it.exprivia.location.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanimetriaEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void pubblicaEliminazione(PlanimetriaEliminataEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_LOCATION,
                RabbitMQConfig.ROUTING_KEY_PLANIMETRIA_ELIMINATA,
                event
        );
    }
}
