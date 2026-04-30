package it.exprivia.utentiservice.service;

import it.exprivia.utenti.entity.Gruppo;
import it.exprivia.utenti.entity.GruppoUtente;
import it.exprivia.utenti.entity.RuoloUtente;
import it.exprivia.utenti.entity.Utente;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GruppoServiceTest {

    @Mock GruppoRepository gruppoRepository;
    @Mock GruppoUtenteRepository gruppoUtenteRepository;
    @Mock UtenteRepository utenteRepository;
    @Mock GruppoEventPublisher gruppoEventPublisher;

    @InjectMocks GruppoService gruppoService;

    @Test
    void aggiungiUtente_receptionNonPuoGestireAdminOBuildingManagerOReception() {
        Gruppo gruppo = new Gruppo();
        gruppo.setId(3L);
        gruppo.setNome("ITS");
        when(gruppoRepository.existsById(3L)).thenReturn(true);
        when(utenteRepository.findByEmail("reception@exprivia.com"))
                .thenReturn(Optional.of(buildUser(10L, "Reception", "reception@exprivia.com", RuoloUtente.RECEPTION)));
        when(utenteRepository.findById(20L))
                .thenReturn(Optional.of(buildUser(20L, "Admin", "admin@exprivia.com", RuoloUtente.ADMIN)));

        assertThatThrownBy(() -> gruppoService.aggiungiUtente(3L, 20L, "reception@exprivia.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("USER o GUEST");

        verify(gruppoUtenteRepository, never()).save(any(GruppoUtente.class));
    }

    @Test
    void aggiungiUtente_receptionPuoGestireUserEGuest() {
        when(gruppoRepository.existsById(3L)).thenReturn(true);
        when(utenteRepository.findByEmail("reception@exprivia.com"))
                .thenReturn(Optional.of(buildUser(10L, "Reception", "reception@exprivia.com", RuoloUtente.RECEPTION)));
        when(utenteRepository.findById(20L))
                .thenReturn(Optional.of(buildUser(20L, "Guest", "guest@exprivia.com", RuoloUtente.GUEST)));
        when(gruppoUtenteRepository.existsByIdGruppoAndIdUtente(3L, 20L)).thenReturn(false);

        gruppoService.aggiungiUtente(3L, 20L, "reception@exprivia.com");

        verify(gruppoUtenteRepository).save(any(GruppoUtente.class));
    }

    @Test
    void crea_normalizzaNomeESalvaGruppo() {
        when(gruppoRepository.existsByNomeIgnoreCase("Team Alpha")).thenReturn(false);
        when(gruppoRepository.save(any(Gruppo.class))).thenAnswer(invocation -> {
            Gruppo gruppo = invocation.getArgument(0);
            gruppo.setId(3L);
            return gruppo;
        });

        Gruppo gruppo = gruppoService.crea("  Team Alpha  ");

        assertThat(gruppo.getId()).isEqualTo(3L);
        assertThat(gruppo.getNome()).isEqualTo("Team Alpha");
        verify(gruppoRepository).save(argThat(saved -> "Team Alpha".equals(saved.getNome())));
    }

    @Test
    void crea_rifiutaNomeDuplicato() {
        when(gruppoRepository.existsByNomeIgnoreCase("Team Alpha")).thenReturn(true);

        assertThatThrownBy(() -> gruppoService.crea("Team Alpha"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Esiste gia'");

        verify(gruppoRepository, never()).save(any(Gruppo.class));
    }

    @Test
    void aggiorna_modificaNomeGruppoEsistente() {
        Gruppo gruppo = new Gruppo();
        gruppo.setId(7L);
        gruppo.setNome("Team Old");
        when(gruppoRepository.findById(7L)).thenReturn(Optional.of(gruppo));
        when(gruppoRepository.existsByNomeIgnoreCaseAndIdNot("Team New", 7L)).thenReturn(false);
        when(gruppoRepository.save(gruppo)).thenReturn(gruppo);

        Gruppo aggiornato = gruppoService.aggiorna(7L, " Team New ");

        assertThat(aggiornato.getNome()).isEqualTo("Team New");
    }

    @Test
    void aggiorna_rifiutaNomeDuplicato() {
        Gruppo gruppo = new Gruppo();
        gruppo.setId(7L);
        gruppo.setNome("Team Old");
        when(gruppoRepository.findById(7L)).thenReturn(Optional.of(gruppo));
        when(gruppoRepository.existsByNomeIgnoreCaseAndIdNot("Team New", 7L)).thenReturn(true);

        assertThatThrownBy(() -> gruppoService.aggiorna(7L, "Team New"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Esiste gia'");

        verify(gruppoRepository, never()).save(any(Gruppo.class));
    }

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

    private Utente buildUser(Long id, String fullName, String email, RuoloUtente ruolo) {
        Utente utente = new Utente();
        utente.setId(id);
        utente.setFullName(fullName);
        utente.setEmail(email);
        utente.setRuolo(ruolo);
        utente.setPasswordHash("hash");
        return utente;
    }
}
