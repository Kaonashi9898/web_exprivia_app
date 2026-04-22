package it.exprivia.utentiservice.service;

import it.exprivia.utenti.messaging.GruppoEventPublisher;
import it.exprivia.utenti.messaging.UtenteEliminatoEvent;
import it.exprivia.utenti.repository.GruppoUtenteRepository;
import it.exprivia.utenti.repository.UtenteRepository;
import it.exprivia.utenti.service.UtenteService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtenteServiceTest {

    @Mock UtenteRepository utenteRepository;
    @Mock GruppoUtenteRepository gruppoUtenteRepository;
    @Mock GruppoEventPublisher gruppoEventPublisher;

    @InjectMocks UtenteService utenteService;

    @Test
    void delete_utenteInesistente_lanceEntityNotFoundException() {
        when(utenteRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> utenteService.delete(1L))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(gruppoUtenteRepository, gruppoEventPublisher);
    }

    @Test
    void delete_pulisceGruppiUtentePrimaDiEliminareUtente() {
        when(utenteRepository.existsById(5L)).thenReturn(true);

        utenteService.delete(5L);

        var ordine = inOrder(gruppoUtenteRepository, utenteRepository);
        ordine.verify(gruppoUtenteRepository).deleteByIdUtente(5L);
        ordine.verify(utenteRepository).deleteById(5L);
    }

    @Test
    void delete_pubblicaEventoDopoEliminazione() {
        when(utenteRepository.existsById(7L)).thenReturn(true);

        utenteService.delete(7L);

        var ordine = inOrder(utenteRepository, gruppoEventPublisher);
        ordine.verify(utenteRepository).deleteById(7L);
        ordine.verify(gruppoEventPublisher).pubblicaEliminazioneUtente(any());
    }

    @Test
    void delete_eventoContieneSoloIdUtente() {
        when(utenteRepository.existsById(9L)).thenReturn(true);

        utenteService.delete(9L);

        ArgumentCaptor<UtenteEliminatoEvent> captor = ArgumentCaptor.forClass(UtenteEliminatoEvent.class);
        verify(gruppoEventPublisher).pubblicaEliminazioneUtente(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new UtenteEliminatoEvent(9L));
    }

    @Test
    void delete_utenteInesistente_nonPubblicaEvento() {
        when(utenteRepository.existsById(99L)).thenReturn(false);

        try { utenteService.delete(99L); } catch (EntityNotFoundException ignored) {}

        verify(gruppoEventPublisher, never()).pubblicaEliminazioneUtente(any());
    }
}
