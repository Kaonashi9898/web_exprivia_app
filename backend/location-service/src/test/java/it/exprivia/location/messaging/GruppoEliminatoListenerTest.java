package it.exprivia.location.messaging;

import it.exprivia.location.repository.GruppoPostazioneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class GruppoEliminatoListenerTest {

    @Mock
    GruppoPostazioneRepository gruppoPostazioneRepository;

    @InjectMocks
    GruppoEliminatoListener listener;

    @Test
    void onGruppoEliminato_cancellaRigheOrfaneConIdCorretto() {
        listener.onGruppoEliminato(new GruppoEliminatoEvent(7L));

        verify(gruppoPostazioneRepository).deleteByGruppoId(7L);
    }

    @Test
    void onGruppoEliminato_nonEseguiAltreOperazioni() {
        listener.onGruppoEliminato(new GruppoEliminatoEvent(3L));

        verify(gruppoPostazioneRepository).deleteByGruppoId(3L);
        verifyNoMoreInteractions(gruppoPostazioneRepository);
    }

    @Test
    void onGruppoEliminato_idDiversiProducanoChiamateDistinte() {
        listener.onGruppoEliminato(new GruppoEliminatoEvent(1L));
        listener.onGruppoEliminato(new GruppoEliminatoEvent(2L));

        verify(gruppoPostazioneRepository).deleteByGruppoId(1L);
        verify(gruppoPostazioneRepository).deleteByGruppoId(2L);
    }
}
