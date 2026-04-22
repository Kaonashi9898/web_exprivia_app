package it.exprivia.location.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UtenteEliminatoListenerTest {

    @InjectMocks
    UtenteEliminatoListener listener;

    @Test
    void onUtenteEliminato_nonLanciaEccezioni() {
        listener.onUtenteEliminato(new UtenteEliminatoEvent(1L));
    }
}
