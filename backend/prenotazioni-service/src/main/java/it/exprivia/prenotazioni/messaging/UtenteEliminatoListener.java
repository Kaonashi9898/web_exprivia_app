package it.exprivia.prenotazioni.messaging;

import it.exprivia.prenotazioni.config.RabbitMQConfig;
import it.exprivia.prenotazioni.service.PrenotazioneService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UtenteEliminatoListener {

    private final PrenotazioneService prenotazioneService;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.QUEUE_UTENTE_ELIMINATO)
    public void onUtenteEliminato(UtenteEliminatoEvent event) {
        prenotazioneService.annullaPrenotazioniFuturePerUtenteEliminato(event.utenteId());
    }
}
