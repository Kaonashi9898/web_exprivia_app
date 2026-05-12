package it.exprivia.location.service;

import it.exprivia.location.dto.SedeRequest;
import it.exprivia.location.entity.Piano;
import it.exprivia.location.entity.Sede;
import it.exprivia.location.repository.PianoRepository;
import it.exprivia.location.repository.SedeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    PianoRepository pianoRepository;

    @Mock
    PlanimetriaService planimetriaService;

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
    void delete_pulisceLeRisorseDiTuttiIPianiPrimaDiEliminareLaSede() {
        Piano primoPiano = new Piano();
        primoPiano.setId(7L);
        Piano secondoPiano = new Piano();
        secondoPiano.setId(8L);

        when(sedeRepository.existsById(12L)).thenReturn(true);
        when(pianoRepository.findByEdificioSedeId(12L)).thenReturn(List.of(primoPiano, secondoPiano));

        sedeService.delete(12L);

        verify(planimetriaService).cleanupResourcesForPianoDeletion(7L);
        verify(planimetriaService).cleanupResourcesForPianoDeletion(8L);
        verify(sedeRepository).deleteById(12L);
    }

    @Test
    void delete_rifiutaSedeInesistente() {
        when(sedeRepository.existsById(12L)).thenReturn(false);

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> sedeService.delete(12L));

        assertEquals("Sede non trovata con id: 12", ex.getMessage());
        verify(sedeRepository, never()).deleteById(12L);
    }
}
