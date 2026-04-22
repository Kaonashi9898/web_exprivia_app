package it.exprivia.location.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UtenteEliminatoListener {

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.QUEUE_UTENTE)
    public void onUtenteEliminato(UtenteEliminatoEvent event) {
        // Location does not store per-user mappings, so this event is intentionally a no-op.
    }
}
