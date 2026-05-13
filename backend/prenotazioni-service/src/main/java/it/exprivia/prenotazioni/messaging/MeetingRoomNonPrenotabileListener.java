package it.exprivia.prenotazioni.messaging;

import it.exprivia.prenotazioni.config.RabbitMQConfig;
import it.exprivia.prenotazioni.service.PrenotazioneService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MeetingRoomNonPrenotabileListener {

    private final PrenotazioneService prenotazioneService;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.QUEUE_MEETING_ROOM_NON_PRENOTABILE)
    public void onMeetingRoomNonPrenotabile(MeetingRoomNonPrenotabileEvent event) {
        prenotazioneService.annullaPrenotazioniFuturePerMeetingRoomNonDisponibile(
                event.stanzaId(),
                event.stato()
        );
    }
}
