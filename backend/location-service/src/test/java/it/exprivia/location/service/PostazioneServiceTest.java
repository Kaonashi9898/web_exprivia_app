package it.exprivia.location.service;

import it.exprivia.location.dto.PostazioneRequest;
import it.exprivia.location.dto.PostazioneResponse;
import it.exprivia.location.entity.Postazione;
import it.exprivia.location.entity.Stanza;
import it.exprivia.location.entity.StatoPostazione;
import it.exprivia.location.messaging.PostazioneEventPublisher;
import it.exprivia.location.messaging.PostazioneNonPrenotabileEvent;
import it.exprivia.location.repository.PostazioneRepository;
import it.exprivia.location.repository.StanzaRepository;
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
class PostazioneServiceTest {

    @Mock
    PostazioneRepository postazioneRepository;

    @Mock
    StanzaRepository stanzaRepository;

    @Mock
    PostazioneEventPublisher postazioneEventPublisher;

    @InjectMocks
    PostazioneService postazioneService;

    @Test
    void update_rifiutaCodiceDuplicatoConBadRequestChiaro() {
        Stanza stanza = new Stanza();
        stanza.setId(10L);
        stanza.setNome("Open Space");

        Postazione current = new Postazione();
        current.setId(1L);
        current.setCodice("PDL-01");
        current.setStato(StatoPostazione.DISPONIBILE);
        current.setStanza(stanza);

        Postazione duplicate = new Postazione();
        duplicate.setId(2L);
        duplicate.setCodice("PDL-02");

        PostazioneRequest request = new PostazioneRequest();
        request.setCodice("PDL-02");
        request.setStanzaId(10L);
        request.setStato(StatoPostazione.MANUTENZIONE);

        when(postazioneRepository.findById(1L)).thenReturn(Optional.of(current));
        when(stanzaRepository.findById(10L)).thenReturn(Optional.of(stanza));
        when(postazioneRepository.findByCodice("PDL-02")).thenReturn(Optional.of(duplicate));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> postazioneService.update(1L, request)
        );

        assertEquals("Postazione già esistente con codice: PDL-02", ex.getMessage());
        verify(postazioneRepository, never()).save(any(Postazione.class));
    }

    @Test
    void update_impostaDisponibileQuandoLoStatoENull() {
        Stanza stanza = new Stanza();
        stanza.setId(10L);
        stanza.setNome("Open Space");

        Postazione current = new Postazione();
        current.setId(1L);
        current.setCodice("PDL-01");
        current.setStato(StatoPostazione.MANUTENZIONE);
        current.setStanza(stanza);

        PostazioneRequest request = new PostazioneRequest();
        request.setCodice("PDL-01");
        request.setStanzaId(10L);
        request.setStato(null);

        when(postazioneRepository.findById(1L)).thenReturn(Optional.of(current));
        when(stanzaRepository.findById(10L)).thenReturn(Optional.of(stanza));
        when(postazioneRepository.findByCodice("PDL-01")).thenReturn(Optional.of(current));
        when(postazioneRepository.save(any(Postazione.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostazioneResponse response = postazioneService.update(1L, request);

        assertEquals(StatoPostazione.DISPONIBILE, response.getStato());
        verify(postazioneRepository).save(current);
        verify(postazioneEventPublisher, never()).pubblicaNonPrenotabile(any(PostazioneNonPrenotabileEvent.class));
    }

    @Test
    void aggiornaStato_pubblicaEventoQuandoLaPostazioneDiventaNonPrenotabile() {
        Stanza stanza = new Stanza();
        stanza.setId(10L);
        stanza.setNome("Open Space");

        Postazione current = new Postazione();
        current.setId(7L);
        current.setCodice("PDL-07");
        current.setStato(StatoPostazione.DISPONIBILE);
        current.setStanza(stanza);

        when(postazioneRepository.findById(7L)).thenReturn(Optional.of(current));
        when(postazioneRepository.save(any(Postazione.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostazioneResponse response = postazioneService.aggiornaStato(7L, StatoPostazione.MANUTENZIONE);

        assertEquals(StatoPostazione.MANUTENZIONE, response.getStato());
        verify(postazioneEventPublisher).pubblicaNonPrenotabile(
                new PostazioneNonPrenotabileEvent(7L, "PDL-07", StatoPostazione.MANUTENZIONE)
        );
    }

    @Test
    void update_nonPubblicaEventoQuandoLaPostazioneRestaDisponibile() {
        Stanza stanza = new Stanza();
        stanza.setId(10L);
        stanza.setNome("Open Space");

        Postazione current = new Postazione();
        current.setId(1L);
        current.setCodice("PDL-01");
        current.setStato(StatoPostazione.DISPONIBILE);
        current.setStanza(stanza);

        PostazioneRequest request = new PostazioneRequest();
        request.setCodice("PDL-01");
        request.setStanzaId(10L);
        request.setStato(StatoPostazione.DISPONIBILE);

        when(postazioneRepository.findById(1L)).thenReturn(Optional.of(current));
        when(stanzaRepository.findById(10L)).thenReturn(Optional.of(stanza));
        when(postazioneRepository.findByCodice("PDL-01")).thenReturn(Optional.of(current));
        when(postazioneRepository.save(any(Postazione.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postazioneService.update(1L, request);

        verify(postazioneEventPublisher, never()).pubblicaNonPrenotabile(any(PostazioneNonPrenotabileEvent.class));
    }
}
