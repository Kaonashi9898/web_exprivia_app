package it.exprivia.location.service;

import it.exprivia.location.dto.EdificioRequest;
import it.exprivia.location.entity.Piano;
import it.exprivia.location.entity.Sede;
import it.exprivia.location.repository.EdificioRepository;
import it.exprivia.location.repository.PianoRepository;
import it.exprivia.location.repository.SedeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    PianoRepository pianoRepository;

    @Mock
    PlanimetriaService planimetriaService;

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
    void delete_pulisceLeRisorseDiTuttiIPianiPrimaDiEliminareLEdificio() {
        Piano primoPiano = new Piano();
        primoPiano.setId(7L);
        Piano secondoPiano = new Piano();
        secondoPiano.setId(8L);

        when(edificioRepository.existsById(9L)).thenReturn(true);
        when(pianoRepository.findByEdificioId(9L)).thenReturn(List.of(primoPiano, secondoPiano));

        edificioService.delete(9L);

        verify(planimetriaService).cleanupResourcesForPianoDeletion(7L);
        verify(planimetriaService).cleanupResourcesForPianoDeletion(8L);
        verify(edificioRepository).deleteById(9L);
    }
}
