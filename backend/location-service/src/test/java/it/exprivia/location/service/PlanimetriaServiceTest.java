package it.exprivia.location.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.exprivia.location.dto.PlanimetriaLayoutDto;
import it.exprivia.location.dto.PlanimetriaPostazioneResponse;
import it.exprivia.location.dto.PlanimetriaResponse;
import it.exprivia.location.entity.FormatoFile;
import it.exprivia.location.entity.Piano;
import it.exprivia.location.entity.Planimetria;
import it.exprivia.location.entity.Postazione;
import it.exprivia.location.entity.Stanza;
import it.exprivia.location.entity.StatoPostazione;
import it.exprivia.location.entity.TipoStanza;
import it.exprivia.location.messaging.PlanimetriaEventPublisher;
import it.exprivia.location.repository.PianoRepository;
import it.exprivia.location.repository.PlanimetriaRepository;
import it.exprivia.location.repository.PostazioneRepository;
import it.exprivia.location.repository.StanzaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanimetriaServiceTest {

    @Mock
    PianoRepository pianoRepository;

    @Mock
    PlanimetriaRepository planimetriaRepository;

    @Mock
    StanzaRepository stanzaRepository;

    @Mock
    PostazioneRepository postazioneRepository;

    @Mock
    PlanimetriaEventPublisher planimetriaEventPublisher;

    @InjectMocks
    PlanimetriaService planimetriaService;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void uploadImage_salvaImmaginePlanimetria() throws Exception {
        ReflectionTestUtils.setField(planimetriaService, "storageDir", tempDir.toString());

        Piano piano = new Piano();
        piano.setId(7L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "open-space.png",
                "image/png",
                "fake-image".getBytes()
        );

        when(pianoRepository.findById(7L)).thenReturn(Optional.of(piano));
        when(planimetriaRepository.findByPianoId(7L)).thenReturn(Optional.empty());
        when(planimetriaRepository.save(any(Planimetria.class))).thenAnswer(invocation -> {
            Planimetria planimetria = invocation.getArgument(0);
            planimetria.setId(99L);
            return planimetria;
        });

        PlanimetriaResponse response = planimetriaService.uploadImage(7L, file);

        assertEquals(99L, response.getId());
        assertEquals(7L, response.getPianoId());
        assertEquals(FormatoFile.PNG, response.getFormatoOriginale());
        assertTrue(Files.walk(tempDir).anyMatch(path -> path.getFileName().toString().endsWith(".png")));
        ArgumentCaptor<Planimetria> planimetriaCaptor = ArgumentCaptor.forClass(Planimetria.class);
        verify(planimetriaRepository).save(planimetriaCaptor.capture());
        Planimetria savedPlanimetria = planimetriaCaptor.getValue();
        assertEquals(FormatoFile.PNG, savedPlanimetria.getFormatoOriginale());
        assertNotNull(savedPlanimetria.getImageName());
        assertEquals(null, savedPlanimetria.getJsonPath());
    }

    @Test
    void importJson_salvaJsonEAggiornaStanzeEPostazioni() throws Exception {
        ReflectionTestUtils.setField(planimetriaService, "storageDir", tempDir.toString());

        Piano piano = new Piano();
        piano.setId(7L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "floor-plan.json",
                "application/json",
                """
                {
                  "exportedAt": "2026-04-09T10:00:00Z",
                  "image": { "filename": "open-space.png", "naturalWidth": 1400, "naturalHeight": 900 },
                  "rooms": [
                    {
                      "id": "room-1",
                      "label": "Open Space",
                      "position": { "xPct": 10.5, "yPct": 20.25 },
                      "stationIds": ["stn-1"]
                    }
                  ],
                  "stations": [
                    {
                      "id": "stn-1",
                      "label": "PDL-01",
                      "position": { "xPct": 18.75, "yPct": 24.50 },
                      "roomId": "room-1",
                      "roomLabel": "Open Space"
                    }
                  ],
                  "connections": [
                    { "stationId": "stn-1", "roomId": "room-1" }
                  ]
                }
                """.getBytes()
        );

        when(pianoRepository.findById(7L)).thenReturn(Optional.of(piano));
        when(stanzaRepository.findByPianoId(7L)).thenReturn(List.of());
        Planimetria[] savedPlanimetria = new Planimetria[1];
        when(planimetriaRepository.findByPianoId(7L)).thenAnswer(invocation -> Optional.ofNullable(savedPlanimetria[0]));
        when(stanzaRepository.save(any(Stanza.class))).thenAnswer(invocation -> {
            Stanza stanza = invocation.getArgument(0);
            stanza.setId(11L);
            return stanza;
        });
        when(postazioneRepository.findByLayoutElementIdAndStanzaPianoId("stn-1", 7L)).thenReturn(Optional.empty());
        when(postazioneRepository.findByCodice("PDL-01")).thenReturn(Optional.empty());
        when(postazioneRepository.findByStanzaPianoId(7L)).thenReturn(List.of());
        when(postazioneRepository.save(any(Postazione.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planimetriaRepository.save(any(Planimetria.class))).thenAnswer(invocation -> {
            Planimetria planimetria = invocation.getArgument(0);
            planimetria.setId(88L);
            planimetria.setPiano(piano);
            savedPlanimetria[0] = planimetria;
            return planimetria;
        });

        PlanimetriaResponse response = planimetriaService.importJson(7L, file);
        List<PlanimetriaPostazioneResponse> postazioni = planimetriaService.getPostazioniByPianoId(7L);

        assertEquals(88L, response.getId());
        assertEquals(1, postazioni.size());
        assertEquals("Open Space", postazioni.get(0).getStanza());
        assertTrue(Files.walk(tempDir).anyMatch(path -> path.getFileName().toString().endsWith(".json")));
        verify(stanzaRepository).save(any(Stanza.class));
        verify(planimetriaRepository).save(any(Planimetria.class));
        ArgumentCaptor<Postazione> postazioneCaptor = ArgumentCaptor.forClass(Postazione.class);
        verify(postazioneRepository).save(postazioneCaptor.capture());
        Postazione savedPostazione = postazioneCaptor.getValue();
        assertEquals("PDL-01", savedPostazione.getCodice());
        assertEquals("stn-1", savedPostazione.getLayoutElementId());
        assertEquals(StatoPostazione.DISPONIBILE, savedPostazione.getStato());
        assertNotNull(savedPostazione.getStanza());
        assertEquals(TipoStanza.ROOM, savedPostazione.getStanza().getTipo());
        assertEquals(new BigDecimal("18.75"), savedPostazione.getXPct());
        assertEquals(new BigDecimal("24.50"), savedPostazione.getYPct());
    }

    @Test
    void deleteByPianoId_cancellaRecordEFile() throws Exception {
        ReflectionTestUtils.setField(planimetriaService, "storageDir", tempDir.toString());

        Piano piano = new Piano();
        piano.setId(3L);

        Path originalPath = Files.writeString(tempDir.resolve("original.png"), "x");
        Path jsonPath = Files.writeString(tempDir.resolve("postazioni.json"), "{}");

        Planimetria planimetria = new Planimetria();
        planimetria.setPiano(piano);
        planimetria.setFileOriginalePath(originalPath.toString());
        planimetria.setImagePath(originalPath.toString());
        planimetria.setJsonPath(jsonPath.toString());

        when(planimetriaRepository.findByPianoId(3L)).thenReturn(Optional.of(planimetria));

        planimetriaService.deleteByPianoId(3L);

        verify(planimetriaRepository).delete(planimetria);
        assertFalse(Files.exists(originalPath));
        assertFalse(Files.exists(jsonPath));
    }

    @Test
    void importJson_aggiornaPostazioneEsistenteTrovataPerCadId() throws Exception {
        ReflectionTestUtils.setField(planimetriaService, "storageDir", tempDir.toString());

        Piano piano = new Piano();
        piano.setId(9L);

        Stanza stanzaEsistente = new Stanza();
        stanzaEsistente.setId(21L);
        stanzaEsistente.setNome("Open Space");
        stanzaEsistente.setPiano(piano);

        Postazione postazioneEsistente = new Postazione();
        postazioneEsistente.setId(44L);
        postazioneEsistente.setCodice("OLD-01");
        postazioneEsistente.setLayoutElementId("stn-9");
        postazioneEsistente.setStato(StatoPostazione.MANUTENZIONE);
        postazioneEsistente.setStanza(stanzaEsistente);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "floor-plan.json",
                "application/json",
                """
                {
                  "rooms": [
                    {
                      "id": "room-9",
                      "label": "Open Space",
                      "position": { "xPct": 10, "yPct": 15 },
                      "stationIds": ["stn-9"]
                    }
                  ],
                  "stations": [
                    {
                      "id": "stn-9",
                      "label": "NEW-01",
                      "position": { "xPct": 12.00, "yPct": 18.00 },
                      "roomId": "room-9",
                      "roomLabel": "Open Space"
                    }
                  ],
                  "connections": [
                    { "stationId": "stn-9", "roomId": "room-9" }
                  ]
                }
                """.getBytes()
        );

        when(pianoRepository.findById(9L)).thenReturn(Optional.of(piano));
        when(stanzaRepository.findByPianoId(9L)).thenReturn(List.of(stanzaEsistente));
        when(stanzaRepository.save(any(Stanza.class))).thenAnswer(invocation -> {
            Stanza stanza = invocation.getArgument(0);
            stanza.setId(31L);
            return stanza;
        });
        when(planimetriaRepository.findByPianoId(9L)).thenReturn(Optional.empty());
        when(postazioneRepository.findByLayoutElementIdAndStanzaPianoId("stn-9", 9L)).thenReturn(Optional.of(postazioneEsistente));
        when(postazioneRepository.findByStanzaPianoId(9L)).thenReturn(List.of(postazioneEsistente));
        when(postazioneRepository.findByCodice("NEW-01")).thenReturn(Optional.empty());
        when(postazioneRepository.save(any(Postazione.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planimetriaRepository.save(any(Planimetria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planimetriaService.importJson(9L, file);

        verify(stanzaRepository).save(any(Stanza.class));
        ArgumentCaptor<Postazione> postazioneCaptor = ArgumentCaptor.forClass(Postazione.class);
        verify(postazioneRepository).save(postazioneCaptor.capture());
        Postazione savedPostazione = postazioneCaptor.getValue();
        assertEquals(Long.valueOf(44L), savedPostazione.getId());
        assertEquals("NEW-01", savedPostazione.getCodice());
        assertEquals("stn-9", savedPostazione.getLayoutElementId());
        assertEquals(StatoPostazione.MANUTENZIONE, savedPostazione.getStato());
        assertEquals(new BigDecimal("12.00"), savedPostazione.getXPct());
        assertEquals(new BigDecimal("18.00"), savedPostazione.getYPct());
        assertNotNull(savedPostazione.getStanza());
    }

    @Test
    void getLayoutByPianoId_leggeJsonSalvato() throws Exception {
        ReflectionTestUtils.setField(planimetriaService, "storageDir", tempDir.toString());

        Path jsonPath = Files.writeString(tempDir.resolve("layout.json"), """
                {
                  "rooms": [
                    {
                      "id": "room-1",
                      "label": "Sala Meeting",
                      "position": { "xPct": 44.1, "yPct": 22.2 },
                      "stationIds": []
                    }
                  ],
                  "stations": [],
                  "connections": []
                }
                """);

        Piano piano = new Piano();
        piano.setId(5L);

        Planimetria planimetria = new Planimetria();
        planimetria.setPiano(piano);
        planimetria.setJsonPath(jsonPath.toString());

        when(planimetriaRepository.findByPianoId(5L)).thenReturn(Optional.of(planimetria));

        PlanimetriaLayoutDto layout = planimetriaService.getLayoutByPianoId(5L);

        assertEquals(1, layout.getRooms().size());
        assertEquals("Sala Meeting", layout.getRooms().get(0).getLabel());
    }

    @Test
    void importJson_salvaMeetingRoomComeTipoStanzaCorretto() throws Exception {
        ReflectionTestUtils.setField(planimetriaService, "storageDir", tempDir.toString());

        Piano piano = new Piano();
        piano.setId(15L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "meeting-layout.json",
                "application/json",
                """
                {
                  "meetings": [
                    {
                      "id": "meeting-1",
                      "label": "Board Room",
                      "position": { "xPct": 41.250, "yPct": 22.500 }
                    }
                  ],
                  "rooms": [],
                  "stations": [],
                  "connections": []
                }
                """.getBytes()
        );

        when(pianoRepository.findById(15L)).thenReturn(Optional.of(piano));
        when(stanzaRepository.findByPianoId(15L)).thenReturn(List.of());
        when(planimetriaRepository.findByPianoId(15L)).thenReturn(Optional.empty());
        when(stanzaRepository.save(any(Stanza.class))).thenAnswer(invocation -> {
            Stanza stanza = invocation.getArgument(0);
            stanza.setId(151L);
            return stanza;
        });
        when(planimetriaRepository.save(any(Planimetria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planimetriaService.importJson(15L, file);

        ArgumentCaptor<Stanza> stanzaCaptor = ArgumentCaptor.forClass(Stanza.class);
        verify(stanzaRepository).save(stanzaCaptor.capture());
        Stanza savedStanza = stanzaCaptor.getValue();
        assertEquals("meeting-1", savedStanza.getLayoutElementId());
        assertEquals(TipoStanza.MEETING_ROOM, savedStanza.getTipo());
        assertEquals(new BigDecimal("41.250"), savedStanza.getXPct());
        assertEquals(new BigDecimal("22.500"), savedStanza.getYPct());
        assertNotNull(savedStanza.getNome());
        assertTrue(savedStanza.getNome().startsWith("Board Room"));
    }

    @Test
    void importJson_supportaExportEditorConNomiDuplicati() throws Exception {
        ReflectionTestUtils.setField(planimetriaService, "storageDir", tempDir.toString());

        Piano piano = new Piano();
        piano.setId(21L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "editor-floor-plan.json",
                "application/json",
                """
                {
                  "exportedAt": "2026-04-27T10:00:00Z",
                  "image": { "filename": "plan.png", "naturalWidth": 1200, "naturalHeight": 900 },
                  "meetings": [],
                  "rooms": [
                    {
                      "id": "room-1",
                      "label": "Open Space",
                      "position": { "xPct": 10.0, "yPct": 20.0 },
                      "stations": [
                        {
                          "id": "stn-1",
                          "label": "Desk",
                          "position": { "xPct": 11.0, "yPct": 21.0 }
                        }
                      ]
                    },
                    {
                      "id": "room-2",
                      "label": "Open Space",
                      "position": { "xPct": 30.0, "yPct": 40.0 },
                      "stations": [
                        {
                          "id": "stn-2",
                          "label": "Desk",
                          "position": { "xPct": 31.0, "yPct": 41.0 }
                        }
                      ]
                    }
                  ]
                }
                """.getBytes()
        );

        when(pianoRepository.findById(21L)).thenReturn(Optional.of(piano));
        when(stanzaRepository.findByPianoId(21L)).thenReturn(List.of());
        when(planimetriaRepository.findByPianoId(21L)).thenReturn(Optional.empty());
        when(stanzaRepository.save(any(Stanza.class))).thenAnswer(invocation -> {
            Stanza stanza = invocation.getArgument(0);
            stanza.setId(stanza.getLayoutElementId() != null && stanza.getLayoutElementId().endsWith("1") ? 201L : 202L);
            return stanza;
        });
        when(postazioneRepository.findByStanzaPianoId(21L)).thenReturn(List.of());
        when(postazioneRepository.findByCodice(anyString())).thenReturn(Optional.empty());
        when(postazioneRepository.save(any(Postazione.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planimetriaRepository.save(any(Planimetria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planimetriaService.importJson(21L, file);

        verify(stanzaRepository, times(2)).save(any(Stanza.class));
        ArgumentCaptor<Postazione> postazioneCaptor = ArgumentCaptor.forClass(Postazione.class);
        verify(postazioneRepository, times(2)).save(postazioneCaptor.capture());
        assertEquals(2, postazioneCaptor.getAllValues().size());
        assertEquals("stn-1", postazioneCaptor.getAllValues().get(0).getLayoutElementId());
        assertEquals("stn-2", postazioneCaptor.getAllValues().get(1).getLayoutElementId());
        assertNotNull(postazioneCaptor.getAllValues().get(0).getCodice());
        assertNotNull(postazioneCaptor.getAllValues().get(1).getCodice());
        assertTrue(!postazioneCaptor.getAllValues().get(0).getCodice().equals(postazioneCaptor.getAllValues().get(1).getCodice()));
    }

    @Test
    void importJson_nonRiusaPostazioneSoloPerCodiceSeLayoutElementIdEDiverso() throws Exception {
        ReflectionTestUtils.setField(planimetriaService, "storageDir", tempDir.toString());

        Piano piano = new Piano();
        piano.setId(31L);

        Stanza stanzaEsistente = new Stanza();
        stanzaEsistente.setId(301L);
        stanzaEsistente.setNome("A8");
        stanzaEsistente.setLayoutElementId("room-a8");
        stanzaEsistente.setPiano(piano);

        Postazione postazioneEsistente = new Postazione();
        postazioneEsistente.setId(401L);
        postazioneEsistente.setCodice("Station 1");
        postazioneEsistente.setLayoutElementId("station-a8-1");
        postazioneEsistente.setStato(StatoPostazione.DISPONIBILE);
        postazioneEsistente.setStanza(stanzaEsistente);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "duplicate-station-labels.json",
                "application/json",
                """
                {
                  "rooms": [
                    {
                      "id": "room-a8",
                      "label": "A8",
                      "position": { "xPct": 10, "yPct": 10 },
                      "stations": [
                        {
                          "id": "station-a8-1",
                          "label": "Station 1",
                          "position": { "xPct": 12, "yPct": 12 }
                        }
                      ]
                    },
                    {
                      "id": "room-b1",
                      "label": "B1",
                      "position": { "xPct": 20, "yPct": 20 },
                      "stations": [
                        {
                          "id": "station-b1-1",
                          "label": "Station 1",
                          "position": { "xPct": 22, "yPct": 22 }
                        }
                      ]
                    }
                  ]
                }
                """.getBytes()
        );

        when(pianoRepository.findById(31L)).thenReturn(Optional.of(piano));
        when(stanzaRepository.findByPianoId(31L)).thenReturn(List.of(stanzaEsistente));
        when(stanzaRepository.save(any(Stanza.class))).thenAnswer(invocation -> {
            Stanza stanza = invocation.getArgument(0);
            if (stanza.getId() == null) {
                stanza.setId("room-a8".equals(stanza.getLayoutElementId()) ? 301L : 302L);
            }
            return stanza;
        });
        when(planimetriaRepository.findByPianoId(31L)).thenReturn(Optional.empty());
        when(postazioneRepository.findByLayoutElementIdAndStanzaPianoId("station-a8-1", 31L)).thenReturn(Optional.of(postazioneEsistente));
        when(postazioneRepository.findByLayoutElementIdAndStanzaPianoId("station-b1-1", 31L)).thenReturn(Optional.empty());
        when(postazioneRepository.findByStanzaPianoId(31L)).thenReturn(List.of(postazioneEsistente));
        when(postazioneRepository.findByCodice("Station 1")).thenReturn(Optional.of(postazioneEsistente));
        when(postazioneRepository.findByCodice("Station 1-31-2")).thenReturn(Optional.empty());
        when(postazioneRepository.save(any(Postazione.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planimetriaRepository.save(any(Planimetria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planimetriaService.importJson(31L, file);

        ArgumentCaptor<Postazione> postazioneCaptor = ArgumentCaptor.forClass(Postazione.class);
        verify(postazioneRepository, times(2)).save(postazioneCaptor.capture());

        Postazione firstSaved = postazioneCaptor.getAllValues().get(0);
        Postazione secondSaved = postazioneCaptor.getAllValues().get(1);

        assertEquals(Long.valueOf(401L), firstSaved.getId());
        assertEquals("station-a8-1", firstSaved.getLayoutElementId());
        assertEquals("Station 1", firstSaved.getCodice());

        assertEquals(null, secondSaved.getId());
        assertEquals("station-b1-1", secondSaved.getLayoutElementId());
        assertEquals("Station 1-31-2", secondSaved.getCodice());
        assertNotNull(secondSaved.getStanza());
        assertEquals("room-b1", secondSaved.getStanza().getLayoutElementId());
    }
}
