package it.exprivia.utentiservice.messaging;

import it.exprivia.utenti.messaging.GruppoEliminatoEvent;
import it.exprivia.utenti.messaging.EventPublicationException;
import it.exprivia.utenti.messaging.GruppoEventPublisher;
import it.exprivia.utenti.messaging.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GruppoEventPublisherTest {

    @Mock
    RabbitTemplate rabbitTemplate;

    @InjectMocks
    GruppoEventPublisher publisher;

    @Test
    void pubblicaEliminazione_inviaMessaggioSullExchangeCorretto() {
        publisher.pubblicaEliminazione(42L);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_GRUPPO_ELIMINATO),
                eq(new GruppoEliminatoEvent(42L))
        );
    }

    @Test
    void pubblicaEliminazione_eventoContieneIdGruppoCorretto() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        publisher.pubblicaEliminazione(99L);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_GRUPPO_ELIMINATO),
                captor.capture()
        );
        GruppoEliminatoEvent evento = (GruppoEliminatoEvent) captor.getValue();
        assertThat(evento.gruppoId()).isEqualTo(99L);
    }

    @Test
    void pubblicaEliminazione_propagaErroreQuandoRabbitMqFallisce() {
        doThrow(new RuntimeException("broker down")).when(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_GRUPPO_ELIMINATO),
                eq(new GruppoEliminatoEvent(12L))
        );

        assertThatThrownBy(() -> publisher.pubblicaEliminazione(12L))
                .isInstanceOf(EventPublicationException.class)
                .hasMessageContaining(RabbitMQConfig.ROUTING_KEY_GRUPPO_ELIMINATO);
    }
}
