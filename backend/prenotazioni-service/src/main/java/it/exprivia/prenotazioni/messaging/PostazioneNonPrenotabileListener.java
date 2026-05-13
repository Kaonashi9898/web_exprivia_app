package it.exprivia.prenotazioni.messaging;

import it.exprivia.prenotazioni.config.RabbitMQConfig;
import it.exprivia.prenotazioni.service.PrenotazioneService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PostazioneNonPrenotabileListener {

    private final PrenotazioneService prenotazioneService;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.QUEUE_POSTAZIONE_NON_PRENOTABILE)
    public void onPostazioneNonPrenotabile(PostazioneNonPrenotabileEvent event) {
        prenotazioneService.annullaPrenotazioniFuturePerPostazioneNonDisponibile(
                event.postazioneId(),
                event.stato()
        );
    }
}
