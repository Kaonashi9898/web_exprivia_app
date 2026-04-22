package it.exprivia.location.messaging;

import it.exprivia.location.repository.GruppoPostazioneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listener RabbitMQ che reagisce all'eliminazione di un gruppo in utenti-service.
 *
 * Quando arriva un messaggio sulla coda {@link RabbitMQConfig#QUEUE},
 * questo componente elimina tutte le associazioni gruppo-postazione
 * relative al gruppo eliminato, mantenendo la consistenza dei dati
 * tra i due microservizi.
 *
 * L'approccio è "eventual consistency": la pulizia avviene in modo asincrono
 * dopo che utenti-service ha già completato l'eliminazione del gruppo.
 */
@Component
@RequiredArgsConstructor
public class GruppoEliminatoListener {

    private final GruppoPostazioneRepository gruppoPostazioneRepository;

    /**
     * Metodo invocato automaticamente da Spring AMQP quando arriva un messaggio.
     * Il messaggio JSON viene deserializzato automaticamente in {@link GruppoEliminatoEvent}.
     *
     * @param event l'evento contenente l'ID del gruppo eliminato
     */
    @Transactional
    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void onGruppoEliminato(GruppoEliminatoEvent event) {
        // Elimina tutte le postazioni assegnate al gruppo eliminato
        gruppoPostazioneRepository.deleteByGruppoId(event.gruppoId());
    }
}
