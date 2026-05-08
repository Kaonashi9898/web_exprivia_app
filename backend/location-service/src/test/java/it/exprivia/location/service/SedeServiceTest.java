package it.exprivia.location.service;

import it.exprivia.location.dto.SedeRequest;
import it.exprivia.location.entity.Postazione;
import it.exprivia.location.entity.Sede;
import it.exprivia.location.messaging.PlanimetriaEliminataEvent;
import it.exprivia.location.messaging.PlanimetriaEventPublisher;
import it.exprivia.location.repository.PostazioneRepository;
import it.exprivia.location.repository.SedeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SedeServiceTest {

    @Mock
    SedeRepository sedeRepository;

    @Mock
    PostazioneRepository postazioneRepository;

    @Mock
    PlanimetriaEventPublisher planimetriaEventPublisher;

    @InjectMocks
    SedeService sedeService;

    @Test
    void create_rifiutaSedeDuplicataNellaStessaCitta() {
        SedeRequest request = new SedeRequest();
        request.setNome("HQ");
        request.setCitta("Milano");

        when(sedeRepository.existsByNomeAndCitta("HQ", "Milano")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> sedeService.create(request));

        assertEquals("Sede già esistente con questo nome in questa città", ex.getMessage());
        verify(sedeRepository, never()).save(org.mockito.ArgumentMatchers.any(Sede.class));
    }

    @Test
    void delete_pubblicaEventoConLePostazioniCoinvolte() {
        Postazione prima = new Postazione();
        prima.setId(7L);
        Postazione seconda = new Postazione();
        seconda.setId(8L);

        when(sedeRepository.existsById(12L)).thenReturn(true);
        when(postazioneRepository.findByStanzaPianoEdificioSedeId(12L)).thenReturn(List.of(prima, seconda));

        sedeService.delete(12L);

        ArgumentCaptor<PlanimetriaEliminataEvent> captor = ArgumentCaptor.forClass(PlanimetriaEliminataEvent.class);
        verify(sedeRepository).deleteById(12L);
        verify(planimetriaEventPublisher).pubblicaEliminazione(captor.capture());
        assertEquals(List.of(7L, 8L), captor.getValue().postazioneIds());
        assertEquals(null, captor.getValue().pianoId());
    }

    @Test
    void delete_rifiutaSedeInesistente() {
        when(sedeRepository.existsById(12L)).thenReturn(false);

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> sedeService.delete(12L));

        assertEquals("Sede non trovata con id: 12", ex.getMessage());
        verify(sedeRepository, never()).deleteById(12L);
    }
}
