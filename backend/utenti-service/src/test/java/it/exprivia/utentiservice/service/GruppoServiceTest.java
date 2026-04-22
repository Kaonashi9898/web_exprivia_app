package it.exprivia.utentiservice.service;

import it.exprivia.utenti.entity.Gruppo;
import it.exprivia.utenti.messaging.GruppoEventPublisher;
import it.exprivia.utenti.repository.GruppoRepository;
import it.exprivia.utenti.repository.GruppoUtenteRepository;
import it.exprivia.utenti.repository.UtenteRepository;
import it.exprivia.utenti.service.GruppoService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GruppoServiceTest {

    @Mock GruppoRepository gruppoRepository;
    @Mock GruppoUtenteRepository gruppoUtenteRepository;
    @Mock UtenteRepository utenteRepository;
    @Mock GruppoEventPublisher gruppoEventPublisher;

    @InjectMocks GruppoService gruppoService;

    @Test
    void elimina_pubblicaEventoDopoEliminazione() {
        Gruppo gruppo = new Gruppo();
        gruppo.setId(10L);
        gruppo.setNome("Team Alpha");
        when(gruppoRepository.findById(10L)).thenReturn(Optional.of(gruppo));

        gruppoService.elimina(10L);

        // verifica che l'eliminazione avvenga prima della pubblicazione
        var ordine = inOrder(gruppoRepository, gruppoEventPublisher);
        ordine.verify(gruppoRepository).delete(gruppo);
        ordine.verify(gruppoEventPublisher).pubblicaEliminazione(10L);
    }

    @Test
    void elimina_restituisceGruppoEliminato() {
        Gruppo gruppo = new Gruppo();
        gruppo.setId(5L);
        gruppo.setNome("Team Beta");
        when(gruppoRepository.findById(5L)).thenReturn(Optional.of(gruppo));

        Gruppo risultato = gruppoService.elimina(5L);

        assertThat(risultato.getId()).isEqualTo(5L);
        assertThat(risultato.getNome()).isEqualTo("Team Beta");
    }

    @Test
    void elimina_gruppoInesistente_lanceEntityNotFoundException() {
        when(gruppoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gruppoService.elimina(99L))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(gruppoEventPublisher);
    }

    @Test
    void elimina_gruppoInesistente_nonPubblicaEvento() {
        when(gruppoRepository.findById(1L)).thenReturn(Optional.empty());

        try { gruppoService.elimina(1L); } catch (EntityNotFoundException ignored) {}

        verify(gruppoEventPublisher, never()).pubblicaEliminazione(any());
    }
}
