package it.exprivia.location.service;

import it.exprivia.location.dto.EdificioRequest;
import it.exprivia.location.entity.Postazione;
import it.exprivia.location.entity.Sede;
import it.exprivia.location.messaging.PlanimetriaEliminataEvent;
import it.exprivia.location.messaging.PlanimetriaEventPublisher;
import it.exprivia.location.repository.EdificioRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdificioServiceTest {

    @Mock
    EdificioRepository edificioRepository;

    @Mock
    SedeRepository sedeRepository;

    @Mock
    PostazioneRepository postazioneRepository;

    @Mock
    PlanimetriaEventPublisher planimetriaEventPublisher;

    @InjectMocks
    EdificioService edificioService;

    @Test
    void create_rifiutaSedePadreInesistente() {
        EdificioRequest request = new EdificioRequest();
        request.setNome("Torre A");
        request.setSedeId(5L);

        when(sedeRepository.findById(5L)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> edificioService.create(request));

        assertEquals("Sede non trovata con id: 5", ex.getMessage());
    }

    @Test
    void create_rifiutaEdificioDuplicatoNellaSede() {
        EdificioRequest request = new EdificioRequest();
        request.setNome("Torre A");
        request.setSedeId(5L);

        Sede sede = new Sede();
        sede.setId(5L);
        sede.setNome("HQ");

        when(sedeRepository.findById(5L)).thenReturn(Optional.of(sede));
        when(edificioRepository.existsByNomeAndSedeId("Torre A", 5L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> edificioService.create(request));

        assertEquals("Edificio già esistente con questo nome in questa sede", ex.getMessage());
    }

    @Test
    void delete_pubblicaEventoConLePostazioniCoinvolte() {
        Postazione prima = new Postazione();
        prima.setId(7L);
        Postazione seconda = new Postazione();
        seconda.setId(8L);

        when(edificioRepository.existsById(9L)).thenReturn(true);
        when(postazioneRepository.findByStanzaPianoEdificioId(9L)).thenReturn(List.of(prima, seconda));

        edificioService.delete(9L);

        ArgumentCaptor<PlanimetriaEliminataEvent> captor = ArgumentCaptor.forClass(PlanimetriaEliminataEvent.class);
        verify(edificioRepository).deleteById(9L);
        verify(planimetriaEventPublisher).pubblicaEliminazione(captor.capture());
        assertEquals(List.of(7L, 8L), captor.getValue().postazioneIds());
        assertEquals(null, captor.getValue().pianoId());
    }
}
