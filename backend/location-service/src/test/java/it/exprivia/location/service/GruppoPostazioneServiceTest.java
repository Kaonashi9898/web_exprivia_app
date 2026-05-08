package it.exprivia.location.service;

import it.exprivia.location.dto.GruppoPostazioneResponse;
import it.exprivia.location.entity.GruppoPostazione;
import it.exprivia.location.entity.Postazione;
import it.exprivia.location.repository.GruppoPostazioneRepository;
import it.exprivia.location.repository.PostazioneRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GruppoPostazioneServiceTest {

    @Mock
    GruppoPostazioneRepository gruppoPostazioneRepository;

    @Mock
    PostazioneRepository postazioneRepository;

    @InjectMocks
    GruppoPostazioneService gruppoPostazioneService;

    @Test
    void aggiungi_rifiutaAssociazioneDuplicata() {
        Postazione postazione = new Postazione();
        postazione.setId(7L);
        postazione.setCodice("PDL-07");

        when(postazioneRepository.findById(7L)).thenReturn(Optional.of(postazione));
        when(gruppoPostazioneRepository.existsByGruppoIdAndPostazioneId(3L, 7L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> gruppoPostazioneService.aggiungi(3L, 7L));

        assertEquals("Associazione già esistente", ex.getMessage());
        verify(gruppoPostazioneRepository, never()).save(any(GruppoPostazione.class));
    }

    @Test
    void aggiungi_salvaAssociazioneValida() {
        Postazione postazione = new Postazione();
        postazione.setId(7L);
        postazione.setCodice("PDL-07");

        when(postazioneRepository.findById(7L)).thenReturn(Optional.of(postazione));
        when(gruppoPostazioneRepository.existsByGruppoIdAndPostazioneId(3L, 7L)).thenReturn(false);
        when(gruppoPostazioneRepository.save(any(GruppoPostazione.class))).thenAnswer(invocation -> {
            GruppoPostazione gp = invocation.getArgument(0);
            gp.setId(99L);
            return gp;
        });

        GruppoPostazioneResponse response = gruppoPostazioneService.aggiungi(3L, 7L);

        assertEquals(99L, response.getId());
        assertEquals(3L, response.getGruppoId());
        assertEquals(7L, response.getPostazioneId());
        assertEquals("PDL-07", response.getPostazioneCodice());
    }

    @Test
    void rimuovi_rifiutaAssociazioneInesistente() {
        when(gruppoPostazioneRepository.existsByGruppoIdAndPostazioneId(3L, 7L)).thenReturn(false);

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> gruppoPostazioneService.rimuovi(3L, 7L));

        assertEquals("Associazione non trovata", ex.getMessage());
        verify(gruppoPostazioneRepository, never()).deleteByGruppoIdAndPostazioneId(3L, 7L);
    }
}
