package it.exprivia.prenotazioni;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
