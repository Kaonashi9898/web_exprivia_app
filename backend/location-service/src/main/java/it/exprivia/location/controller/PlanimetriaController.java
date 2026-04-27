package it.exprivia.location.controller;

import it.exprivia.location.dto.PlanimetriaPostazioneResponse;
import it.exprivia.location.dto.PlanimetriaLayoutDto;
import it.exprivia.location.dto.PlanimetriaResponse;
import it.exprivia.location.service.PlanimetriaService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Endpoint REST per upload e recupero della planimetria associata a un piano.
 */
@RestController
@RequestMapping("/api/piani/{pianoId}/planimetria")
@RequiredArgsConstructor
public class PlanimetriaController {

    private final PlanimetriaService planimetriaService;

    /** Restituisce i metadati della planimetria del piano. */
    @GetMapping
    public ResponseEntity<PlanimetriaResponse> getByPianoId(@PathVariable Long pianoId) {
        try {
            return ResponseEntity.ok(planimetriaService.findByPianoId(pianoId));
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.noContent().build();
        }
    }

    /** Restituisce il layout completo esportato dall'editor esterno. */
    @GetMapping("/layout")
    public ResponseEntity<PlanimetriaLayoutDto> getLayout(@PathVariable Long pianoId) {
        return ResponseEntity.ok(planimetriaService.getLayoutByPianoId(pianoId));
    }

    /** Restituisce le postazioni derivate dal layout JSON salvato. */
    @GetMapping("/postazioni")
    public ResponseEntity<List<PlanimetriaPostazioneResponse>> getPostazioni(@PathVariable Long pianoId) {
        return ResponseEntity.ok(planimetriaService.getPostazioniByPianoId(pianoId));
    }

    /** Restituisce l'immagine della planimetria per il frontend. */
    @GetMapping("/image")
    public ResponseEntity<Resource> getImage(@PathVariable Long pianoId) {
        Resource resource = planimetriaService.loadImageByPianoId(pianoId);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /** Carica l'immagine base della planimetria (png, jpg/jpeg, svg). */
    @PostMapping(path = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlanimetriaResponse> uploadImage(@PathVariable Long pianoId,
                                                           @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planimetriaService.uploadImage(pianoId, file));
    }

    /** Importa il JSON esportato dall'editor esterno e sincronizza room, meeting room e postazioni. */
    @PostMapping(path = "/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlanimetriaResponse> importJson(@PathVariable Long pianoId,
                                                          @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planimetriaService.importJson(pianoId, file));
    }

    /** Elimina la planimetria e i file derivati associati al piano. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable Long pianoId) {
        planimetriaService.deleteByPianoId(pianoId);
        return ResponseEntity.noContent().build();
    }
}
