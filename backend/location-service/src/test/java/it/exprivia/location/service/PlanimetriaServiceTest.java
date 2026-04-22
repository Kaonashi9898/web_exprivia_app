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
import it.exprivia.location.entity.TipoPostazione;
import it.exprivia.location.repository.PianoRepository;
import it.exprivia.location.repository.PlanimetriaRepository;
import it.exprivia.location.repository.PostazioneRepository;
import it.exprivia.location.repository.StanzaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
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
        verify(planimetriaRepository).save(argThat(planimetria ->
                FormatoFile.PNG == planimetria.getFormatoOriginale()
                        && planimetria.getImageName() != null
                        && planimetria.getJsonPath() == null
        ));
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
        when(postazioneRepository.findByCadIdAndStanzaPianoId("stn-1", 7L)).thenReturn(Optional.empty());
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
        assertEquals(new BigDecimal("0"), response.getCoordXmin());
        assertEquals(new BigDecimal("100"), response.getCoordXmax());
        assertEquals(new BigDecimal("0"), response.getCoordYmin());
        assertEquals(new BigDecimal("100"), response.getCoordYmax());
        assertEquals(1, postazioni.size());
        assertEquals("Open Space", postazioni.get(0).getStanza());
        assertTrue(Files.walk(tempDir).anyMatch(path -> path.getFileName().toString().endsWith(".json")));
        verify(stanzaRepository).save(any(Stanza.class));
        verify(planimetriaRepository).save(any(Planimetria.class));
        verify(postazioneRepository).save(argThat(postazione ->
                "PDL-01".equals(postazione.getCodice())
                        && "stn-1".equals(postazione.getCadId())
                        && TipoPostazione.OPEN_SPACE == postazione.getTipo()
                        && StatoPostazione.DISPONIBILE == postazione.getStato()
                        && postazione.getStanza() != null
                        && "Open Space".equals(postazione.getStanza().getNome())
                        && new BigDecimal("18.75").compareTo(postazione.getX()) == 0
                        && new BigDecimal("24.50").compareTo(postazione.getY()) == 0
        ));
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
        planimetria.setPngPath(originalPath.toString());
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
        postazioneEsistente.setCadId("stn-9");
        postazioneEsistente.setTipo(TipoPostazione.UFFICIO_PRIVATO);
        postazioneEsistente.setStato(StatoPostazione.MANUTENZIONE);
        postazioneEsistente.setAccessibile(Boolean.TRUE);
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
        when(planimetriaRepository.findByPianoId(9L)).thenReturn(Optional.empty());
        when(postazioneRepository.findByCadIdAndStanzaPianoId("stn-9", 9L)).thenReturn(Optional.of(postazioneEsistente));
        when(postazioneRepository.findByStanzaPianoId(9L)).thenReturn(List.of(postazioneEsistente));
        when(postazioneRepository.findByCodice("NEW-01")).thenReturn(Optional.empty());
        when(postazioneRepository.save(any(Postazione.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planimetriaRepository.save(any(Planimetria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planimetriaService.importJson(9L, file);

        verify(stanzaRepository, never()).save(any(Stanza.class));
        verify(postazioneRepository).save(argThat(postazione ->
                Long.valueOf(44L).equals(postazione.getId())
                        && "NEW-01".equals(postazione.getCodice())
                        && "stn-9".equals(postazione.getCadId())
                        && TipoPostazione.UFFICIO_PRIVATO == postazione.getTipo()
                        && StatoPostazione.MANUTENZIONE == postazione.getStato()
                        && Boolean.TRUE.equals(postazione.getAccessibile())
                        && new BigDecimal("12.00").compareTo(postazione.getX()) == 0
                        && new BigDecimal("18.00").compareTo(postazione.getY()) == 0
        ));
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
}
