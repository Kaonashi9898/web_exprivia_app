package it.exprivia.utentiservice.exception;

import it.exprivia.utenti.exception.GlobalExceptionHandler;
import it.exprivia.utenti.messaging.EventPublicationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleEventPublication_restituisceServiceUnavailableConMessaggioChiaro() {
        var response = handler.handleEventPublication(new EventPublicationException("rabbit failure", new RuntimeException("down")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errore()).contains("Nessuna modifica e' stata salvata");
    }
}
