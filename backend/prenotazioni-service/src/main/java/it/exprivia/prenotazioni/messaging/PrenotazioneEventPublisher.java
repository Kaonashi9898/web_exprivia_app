package it.exprivia.prenotazioni.messaging;

import it.exprivia.prenotazioni.config.RabbitMQConfig;
import it.exprivia.prenotazioni.dto.PrenotazioneResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrenotazioneEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void pubblicaConferma(PrenotazioneResponse prenotazione) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_PRENOTAZIONI,
                RabbitMQConfig.ROUTING_KEY_PRENOTAZIONE_CONFERMATA,
                new PrenotazioneConfermataEvent(
                        prenotazione.getId(),
                        prenotazione.getUtenteId(),
                        prenotazione.getUtenteEmail(),
                        prenotazione.getUtenteFullName(),
                        prenotazione.getTipoRisorsaPrenotata(),
                        prenotazione.getRisorsaLabel(),
                        prenotazione.getPostazioneId(),
                        prenotazione.getPostazioneCodice(),
                        prenotazione.getMeetingRoomStanzaId(),
                        prenotazione.getMeetingRoomNome(),
                        prenotazione.getStanzaId(),
                        prenotazione.getStanzaNome(),
                        prenotazione.getDataPrenotazione(),
                        prenotazione.getOraInizio(),
                        prenotazione.getOraFine()
                )
        );
    }

    public void pubblicaAnnullamento(PrenotazioneResponse prenotazione) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_PRENOTAZIONI,
                RabbitMQConfig.ROUTING_KEY_PRENOTAZIONE_ANNULLATA,
                new PrenotazioneAnnullataEvent(
                        prenotazione.getId(),
                        prenotazione.getUtenteId(),
                        prenotazione.getUtenteEmail(),
                        prenotazione.getUtenteFullName(),
                        prenotazione.getTipoRisorsaPrenotata(),
                        prenotazione.getRisorsaLabel(),
                        prenotazione.getPostazioneId(),
                        prenotazione.getPostazioneCodice(),
                        prenotazione.getMeetingRoomStanzaId(),
                        prenotazione.getMeetingRoomNome(),
                        prenotazione.getStanzaId(),
                        prenotazione.getStanzaNome(),
                        prenotazione.getDataPrenotazione(),
                        prenotazione.getOraInizio(),
                        prenotazione.getOraFine()
                )
        );
    }
}
