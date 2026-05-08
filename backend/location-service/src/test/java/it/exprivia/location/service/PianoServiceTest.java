package it.exprivia.location.service;

import it.exprivia.location.dto.PianoRequest;
import it.exprivia.location.dto.PianoResponse;
import it.exprivia.location.entity.Edificio;
import it.exprivia.location.entity.Piano;
import it.exprivia.location.repository.EdificioRepository;
import it.exprivia.location.repository.PianoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PianoServiceTest {

    @Mock
    PianoRepository pianoRepository;

    @Mock
    EdificioRepository edificioRepository;

    @InjectMocks
    PianoService pianoService;

    @Test
    void create_rifiutaNumeroDuplicatoNelloStessoEdificio() {
        PianoRequest request = new PianoRequest();
        request.setNumero(2);
        request.setNome("Secondo piano");
        request.setEdificioId(4L);

        Edificio edificio = new Edificio();
        edificio.setId(4L);
        edificio.setNome("Torre A");

        when(edificioRepository.findById(4L)).thenReturn(Optional.of(edificio));
        when(pianoRepository.existsByNumeroAndEdificioId(2, 4L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> pianoService.create(request));

        assertEquals("Piano 2 già esistente in questo edificio", ex.getMessage());
    }

    @Test
    void create_normalizzaNomeVuotoANull() {
        PianoRequest request = new PianoRequest();
        request.setNumero(2);
        request.setNome("   ");
        request.setEdificioId(4L);

        Edificio edificio = new Edificio();
        edificio.setId(4L);
        edificio.setNome("Torre A");

        when(edificioRepository.findById(4L)).thenReturn(Optional.of(edificio));
        when(pianoRepository.existsByNumeroAndEdificioId(2, 4L)).thenReturn(false);
        when(pianoRepository.save(any(Piano.class))).thenAnswer(invocation -> {
            Piano piano = invocation.getArgument(0);
            piano.setId(10L);
            return piano;
        });

        PianoResponse response = pianoService.create(request);

        assertEquals(10L, response.getId());
        assertNull(response.getNome());
    }

    @Test
    void findByEdificioId_rifiutaEdificioInesistente() {
        when(edificioRepository.existsById(99L)).thenReturn(false);

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> pianoService.findByEdificioId(99L));

        assertEquals("Edificio non trovato con id: 99", ex.getMessage());
    }
}
