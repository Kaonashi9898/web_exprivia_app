package it.exprivia.prenotazioni.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.exprivia.prenotazioni.config.RabbitMQConfig;
import it.exprivia.prenotazioni.service.PrenotazioneService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlanimetriaEliminataListener {

    private final PrenotazioneService prenotazioneService;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.QUEUE_PLANIMETRIA_ELIMINATA)
    public void onPlanimetriaEliminata(PlanimetriaEliminataEvent event) {
        prenotazioneService.eliminaPrenotazioniPerPlanimetria(event.postazioneIds());
    }
}
