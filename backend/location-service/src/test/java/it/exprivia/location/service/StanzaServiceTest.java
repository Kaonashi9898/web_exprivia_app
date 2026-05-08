package it.exprivia.location.service;

import it.exprivia.location.dto.StanzaRequest;
import it.exprivia.location.dto.StanzaResponse;
import it.exprivia.location.entity.Piano;
import it.exprivia.location.entity.Stanza;
import it.exprivia.location.entity.TipoStanza;
import it.exprivia.location.repository.PianoRepository;
import it.exprivia.location.repository.StanzaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StanzaServiceTest {

    @Mock
    StanzaRepository stanzaRepository;

    @Mock
    PianoRepository pianoRepository;

    @InjectMocks
    StanzaService stanzaService;

    @Test
    void create_rifiutaStanzaDuplicataSulPiano() {
        StanzaRequest request = new StanzaRequest();
        request.setNome("Open Space");
        request.setTipo(TipoStanza.ROOM);
        request.setPianoId(7L);

        Piano piano = new Piano();
        piano.setId(7L);
        piano.setNumero(2);

        when(pianoRepository.findById(7L)).thenReturn(Optional.of(piano));
        when(stanzaRepository.existsByNomeAndPianoId("Open Space", 7L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> stanzaService.create(request));

        assertEquals("Stanza già esistente con questo nome su questo piano", ex.getMessage());
    }

    @Test
    void update_rifiutaPianoPadreInesistente() {
        Stanza current = new Stanza();
        current.setId(3L);
        current.setNome("Open Space");

        StanzaRequest request = new StanzaRequest();
        request.setNome("Sala Focus");
        request.setTipo(TipoStanza.MEETING_ROOM);
        request.setPianoId(9L);

        when(stanzaRepository.findById(3L)).thenReturn(Optional.of(current));
        when(pianoRepository.findById(9L)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> stanzaService.update(3L, request));

        assertEquals("Piano non trovato con id: 9", ex.getMessage());
    }

    @Test
    void create_salvaCampiDiLayout() {
        StanzaRequest request = new StanzaRequest();
        request.setNome("Sala Focus");
        request.setTipo(TipoStanza.MEETING_ROOM);
        request.setLayoutElementId("room-7");
        request.setXPct(new BigDecimal("10.5"));
        request.setYPct(new BigDecimal("22.4"));
        request.setPianoId(9L);

        Piano piano = new Piano();
        piano.setId(9L);
        piano.setNumero(3);

        when(pianoRepository.findById(9L)).thenReturn(Optional.of(piano));
        when(stanzaRepository.existsByNomeAndPianoId("Sala Focus", 9L)).thenReturn(false);
        when(stanzaRepository.save(any(Stanza.class))).thenAnswer(invocation -> {
            Stanza stanza = invocation.getArgument(0);
            stanza.setId(4L);
            return stanza;
        });

        StanzaResponse response = stanzaService.create(request);

        assertEquals(4L, response.getId());
        assertEquals("room-7", response.getLayoutElementId());
        assertEquals(new BigDecimal("10.5"), response.getXPct());
        assertEquals(new BigDecimal("22.4"), response.getYPct());
    }
}
