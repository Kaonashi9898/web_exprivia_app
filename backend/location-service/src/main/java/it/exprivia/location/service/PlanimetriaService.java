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
import it.exprivia.location.messaging.PlanimetriaEliminataEvent;
import it.exprivia.location.messaging.PlanimetriaEventPublisher;
import it.exprivia.location.repository.PianoRepository;
import it.exprivia.location.repository.PlanimetriaRepository;
import it.exprivia.location.repository.PostazioneRepository;
import it.exprivia.location.repository.StanzaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gestisce upload immagine, import JSON e recupero delle planimetrie di un piano.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanimetriaService {

    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "svg", "dxf", "dwg");

    private final PianoRepository pianoRepository;
    private final PlanimetriaRepository planimetriaRepository;
    private final StanzaRepository stanzaRepository;
    private final PostazioneRepository postazioneRepository;
    private final PlanimetriaEventPublisher planimetriaEventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${planimetria.storage-dir:storage/planimetrie}")
    private String storageDir;

    public PlanimetriaResponse findByPianoId(Long pianoId) {
        getPianoOrThrow(pianoId);
        return toResponse(getByPianoIdOrThrow(pianoId));
    }

    public Optional<PlanimetriaResponse> findOptionalByPianoId(Long pianoId) {
        getPianoOrThrow(pianoId);
        return planimetriaRepository.findByPianoId(pianoId)
                .map(this::toResponse);
    }

    public PlanimetriaLayoutDto getLayoutByPianoId(Long pianoId) {
        Planimetria planimetria = getByPianoIdOrThrow(pianoId);
        if (planimetria.getJsonPath() == null || planimetria.getJsonPath().isBlank()) {
            throw new EntityNotFoundException("JSON planimetria non trovato per il piano con id: " + pianoId);
        }
        try {
            PlanimetriaLayoutDto layout = objectMapper.readValue(resolvePath(planimetria.getJsonPath()).toFile(), PlanimetriaLayoutDto.class);
            return normalizeLayout(layout);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossibile leggere il layout JSON della planimetria", ex);
        }
    }

    public List<PlanimetriaPostazioneResponse> getPostazioniByPianoId(Long pianoId) {
        PlanimetriaLayoutDto layout = getLayoutByPianoId(pianoId);
        Map<String, String> roomLabelsById = new HashMap<>();
        for (PlanimetriaLayoutDto.RoomDto room : safeList(layout.getRooms())) {
            roomLabelsById.put(room.getId(), normalizeText(room.getLabel()));
        }
        for (PlanimetriaLayoutDto.RoomDto meeting : safeList(layout.getMeetings())) {
            roomLabelsById.put(meeting.getId(), normalizeText(meeting.getLabel()));
        }

        return safeList(layout.getStations()).stream()
                .map(station -> new PlanimetriaPostazioneResponse(
                        normalizeText(station.getId()),
                        normalizeText(station.getLabel()),
                        normalizeText(firstNonBlank(station.getRoomLabel(), roomLabelsById.get(station.getRoomId()))),
                        station.getPosition() != null ? station.getPosition().getXPct() : null,
                        station.getPosition() != null ? station.getPosition().getYPct() : null
                ))
                .toList();
    }

    public Resource loadImageByPianoId(Long pianoId) {
        Planimetria planimetria = getByPianoIdOrThrow(pianoId);
        Path imagePath = resolveImagePath(planimetria);
        if (imagePath == null || !Files.exists(imagePath)) {
            throw new EntityNotFoundException("Immagine planimetria non trovata per il piano con id: " + pianoId);
        }
        return new FileSystemResource(imagePath);
    }

    @Transactional
    public PlanimetriaResponse uploadImage(Long pianoId, MultipartFile file) {
        return uploadImage(pianoId, file, null);
    }

    @Transactional
    public PlanimetriaResponse uploadImage(Long pianoId, MultipartFile file, MultipartFile previewSvg) {
        validateImageFile(file);
        validatePreviewSvgFile(previewSvg);

        Piano piano = getPianoOrThrow(pianoId);
        try {
            Path pianoDir = ensurePianoDirectory(pianoId);
            String extension = getExtension(file.getOriginalFilename(), "png");
            String baseName = buildBaseName(file.getOriginalFilename(), pianoId);
            Path originalPath = pianoDir.resolve(baseName + "." + extension);
            writeMultipartFile(originalPath, file);

            Path previewPath = null;
            if (previewSvg != null && !previewSvg.isEmpty()) {
                previewPath = pianoDir.resolve(baseName + "-preview.svg");
                writeMultipartFile(previewPath, previewSvg);
            }
            Path imagePath = previewPath != null ? previewPath : originalPath;

            Planimetria existing = planimetriaRepository.findByPianoId(pianoId).orElse(null);
            String oldOriginalPath = existing != null ? existing.getFileOriginalePath() : null;
            String oldImagePath = existing != null ? existing.getImagePath() : null;

            Planimetria planimetria = existing != null ? existing : new Planimetria();
            planimetria.setPiano(piano);
            planimetria.setFileOriginalePath(normalizePath(originalPath));
            planimetria.setImagePath(normalizePath(imagePath));
            planimetria.setImageName(imagePath.getFileName().toString());
            planimetria.setFormatoOriginale(resolveFormato(extension));
            if (existing == null) {
                piano.setPlanimetria(planimetria);
            }

            Planimetria saved = planimetriaRepository.save(planimetria);
            deleteIfReplaced(oldOriginalPath, saved.getFileOriginalePath());
            deleteIfReplaced(oldImagePath, saved.getImagePath());
            return toResponse(saved);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossibile salvare l'immagine della planimetria", ex);
        }
    }

    @Transactional
    public PlanimetriaResponse importJson(Long pianoId, MultipartFile file) {
        validateJsonFile(file);

        Piano piano = getPianoOrThrow(pianoId);
        try {
            PlanimetriaLayoutDto layout = normalizeLayout(objectMapper.readValue(file.getInputStream(), PlanimetriaLayoutDto.class));
            validateLayout(layout);

            Path pianoDir = ensurePianoDirectory(pianoId);
            String baseName = buildBaseName(file.getOriginalFilename(), pianoId);
            Path jsonPath = pianoDir.resolve(baseName + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), layout);
            importLayout(piano, layout);

            Planimetria existing = planimetriaRepository.findByPianoId(pianoId).orElse(null);
            String oldJsonPath = existing != null ? existing.getJsonPath() : null;

            Planimetria planimetria = existing != null ? existing : new Planimetria();
            planimetria.setPiano(piano);
            planimetria.setJsonPath(normalizePath(jsonPath));
            if (existing == null) {
                piano.setPlanimetria(planimetria);
            }

            Planimetria saved = planimetriaRepository.save(planimetria);
            deleteIfReplaced(oldJsonPath, saved.getJsonPath());
            return toResponse(saved);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Il file JSON della planimetria non e' valido", ex);
        }
    }

    private void importLayout(Piano piano, PlanimetriaLayoutDto layout) {
        List<Stanza> existingStanze = stanzaRepository.findByPianoId(piano.getId());
        List<Postazione> existingPostazioni = postazioneRepository.findByStanzaPianoId(piano.getId());
        Map<String, Stanza> stanzePerNome = new HashMap<>();
        Map<String, Stanza> stanzePerLayoutId = new HashMap<>();
        for (Stanza stanza : existingStanze) {
            stanzePerNome.put(normalizeStanzaName(stanza.getNome()), stanza);
            String layoutElementId = normalizeText(stanza.getLayoutElementId());
            if (layoutElementId != null) {
                stanzePerLayoutId.put(layoutElementId, stanza);
            }
        }

        Set<String> importedStanzaNames = new HashSet<>(stanzePerNome.keySet());
        Set<String> importedPostazioneCodes = new HashSet<>();
        for (Postazione existing : existingPostazioni) {
            String existingCode = normalizeText(existing.getCodice());
            if (existingCode != null) {
                importedPostazioneCodes.add(normalizeText(existingCode));
            }
        }

        Map<String, Stanza> stanzePerRoomId = new HashMap<>();
        Set<Long> retainedStanzaIds = new HashSet<>();
        for (PlanimetriaLayoutDto.RoomDto importedRoom : safeList(layout.getRooms())) {
            Stanza stanza = upsertStanzaFromLayout(
                    importedRoom,
                    TipoStanza.ROOM,
                    piano,
                    stanzePerNome,
                    stanzePerLayoutId,
                    stanzePerRoomId,
                    importedStanzaNames
            );
            if (stanza.getId() != null) {
                retainedStanzaIds.add(stanza.getId());
            }
        }
        for (PlanimetriaLayoutDto.RoomDto importedMeeting : safeList(layout.getMeetings())) {
            Stanza stanza = upsertStanzaFromLayout(
                    importedMeeting,
                    TipoStanza.MEETING_ROOM,
                    piano,
                    stanzePerNome,
                    stanzePerLayoutId,
                    stanzePerRoomId,
                    importedStanzaNames
            );
            if (stanza.getId() != null) {
                retainedStanzaIds.add(stanza.getId());
            }
        }

        Set<Long> retainedPostazioneIds = new HashSet<>();
        for (PlanimetriaLayoutDto.StationDto importedStation : safeList(layout.getStations())) {
            String layoutElementId = normalizeText(importedStation.getId());
            String codiceBase = normalizeText(importedStation.getLabel());
            String codice = codiceBase != null ? codiceBase : layoutElementId;
            if (codice == null) {
                continue;
            }

            Stanza stanza = stanzePerRoomId.get(importedStation.getRoomId());
            if (stanza == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La postazione " + firstNonBlank(importedStation.getLabel(), importedStation.getId())
                                + " fa riferimento a una stanza inesistente");
            }

            Postazione postazione = findExistingPostazione(piano.getId(), layoutElementId, codice)
                    .orElseGet(Postazione::new);

            if (postazione.getStato() == null) {
                postazione.setStato(StatoPostazione.DISPONIBILE);
            }

            postazione.setCodice(resolveUniqueCodice(codice, postazione, importedPostazioneCodes));
            postazione.setLayoutElementId(layoutElementId);
            postazione.setXPct(importedStation.getPosition() != null ? importedStation.getPosition().getXPct() : null);
            postazione.setYPct(importedStation.getPosition() != null ? importedStation.getPosition().getYPct() : null);
            postazione.setStanza(stanza);
            postazioneRepository.save(postazione);
            importedPostazioneCodes.add(normalizeText(postazione.getCodice()));
            if (postazione.getId() != null) {
                retainedPostazioneIds.add(postazione.getId());
            }
        }

        List<Long> deletedPostazioneIds = new ArrayList<>();
        List<Postazione> obsoletePostazioni = existingPostazioni.stream()
                .filter(existing -> existing.getId() != null && !retainedPostazioneIds.contains(existing.getId()))
                .toList();
        for (Postazione obsolete : obsoletePostazioni) {
            deletedPostazioneIds.add(obsolete.getId());
        }
        if (!obsoletePostazioni.isEmpty()) {
            postazioneRepository.deleteAll(obsoletePostazioni);
        }

        List<Stanza> obsoleteStanze = existingStanze.stream()
                .filter(existing -> existing.getId() != null && !retainedStanzaIds.contains(existing.getId()))
                .toList();
        if (!obsoleteStanze.isEmpty()) {
            stanzaRepository.deleteAll(obsoleteStanze);
        }

        if (!deletedPostazioneIds.isEmpty()) {
            planimetriaEventPublisher.pubblicaEliminazione(new PlanimetriaEliminataEvent(piano.getId(), deletedPostazioneIds));
        }
    }

    @Transactional
    public void deleteByPianoId(Long pianoId) {
        cleanupResourcesForPianoDeletion(pianoId, getByPianoIdOrThrow(pianoId));
    }

    @Transactional
    public void cleanupResourcesForPianoDeletion(Long pianoId) {
        cleanupResourcesForPianoDeletion(pianoId, planimetriaRepository.findByPianoId(pianoId).orElse(null));
    }

    private void cleanupResourcesForPianoDeletion(Long pianoId, Planimetria planimetria) {
        List<Long> postazioneIds = postazioneRepository.findByStanzaPianoId(pianoId).stream()
                .map(Postazione::getId)
                .toList();

        if (planimetria != null) {
            Piano piano = planimetria.getPiano();
            if (piano != null) {
                piano.setPlanimetria(null);
            }
            planimetriaRepository.delete(planimetria);
            deleteQuietly(planimetria.getFileOriginalePath());
            deleteQuietly(planimetria.getImagePath());
            deleteQuietly(planimetria.getJsonPath());
        }

        if (!postazioneIds.isEmpty()) {
            planimetriaEventPublisher.pubblicaEliminazione(new PlanimetriaEliminataEvent(pianoId, postazioneIds));
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Il file immagine della planimetria e' obbligatorio");
        }
        String extension = getExtension(file.getOriginalFilename(), "");
        if (!SUPPORTED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Formato planimetria non supportato. Usa PNG, JPG/JPEG, SVG, DXF o DWG");
        }
    }

    private void validatePreviewSvgFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        if (!"svg".equalsIgnoreCase(getExtension(file.getOriginalFilename(), ""))) {
            throw new IllegalArgumentException("L'anteprima della planimetria deve essere un file SVG");
        }
    }

    private void validateJsonFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Il file JSON della planimetria e' obbligatorio");
        }
        if (!"json".equalsIgnoreCase(getExtension(file.getOriginalFilename(), ""))) {
            throw new IllegalArgumentException("Il file importato deve essere un JSON esportato dall'editor");
        }
    }

    private void validateLayout(PlanimetriaLayoutDto layout) {
        if (layout == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contenuto JSON assente");
        }

        Set<String> roomIds = new HashSet<>();
        for (PlanimetriaLayoutDto.RoomDto room : safeList(layout.getRooms())) {
            String roomId = normalizeText(room.getId());
            if (roomId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ogni stanza deve avere un id");
            }
            if (!roomIds.add(roomId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id stanza duplicato: " + roomId);
            }
        }

        Set<String> meetingIds = new HashSet<>();
        for (PlanimetriaLayoutDto.RoomDto meeting : safeList(layout.getMeetings())) {
            String meetingId = normalizeText(meeting.getId());
            if (meetingId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ogni meeting room deve avere un id");
            }
            if (!meetingIds.add(meetingId) || roomIds.contains(meetingId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id stanza duplicato: " + meetingId);
            }
        }

        Set<String> stationIds = new HashSet<>();
        for (PlanimetriaLayoutDto.StationDto station : safeList(layout.getStations())) {
            String stationId = normalizeText(station.getId());
            if (stationId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ogni postazione deve avere un id");
            }
            if (!stationIds.add(stationId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id postazione duplicato: " + stationId);
            }
            if (!roomIds.contains(normalizeText(station.getRoomId()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La postazione " + firstNonBlank(station.getLabel(), stationId)
                                + " fa riferimento a una stanza inesistente");
            }
        }
    }

    private PlanimetriaLayoutDto normalizeLayout(PlanimetriaLayoutDto layout) {
        if (layout == null) {
            return null;
        }
        if (!safeList(layout.getStations()).isEmpty()) {
            return layout;
        }

        List<PlanimetriaLayoutDto.StationDto> flattenedStations = safeList(layout.getRooms()).stream()
                .flatMap(room -> safeList(room.getStations()).stream().map(station -> {
                    if (station.getRoomId() == null || station.getRoomId().isBlank()) {
                        station.setRoomId(room.getId());
                    }
                    if (station.getRoomLabel() == null || station.getRoomLabel().isBlank()) {
                        station.setRoomLabel(room.getLabel());
                    }
                    return station;
                }))
                .toList();

        layout.setStations(flattenedStations);
        for (PlanimetriaLayoutDto.RoomDto room : safeList(layout.getRooms())) {
            if (safeList(room.getStationIds()).isEmpty() && !safeList(room.getStations()).isEmpty()) {
                room.setStationIds(room.getStations().stream()
                        .map(PlanimetriaLayoutDto.StationDto::getId)
                        .toList());
            }
        }
        return layout;
    }

    private Stanza upsertStanzaFromLayout(PlanimetriaLayoutDto.RoomDto importedRoom,
                                          TipoStanza tipoStanza,
                                          Piano piano,
                                          Map<String, Stanza> stanzePerNome,
                                          Map<String, Stanza> stanzePerLayoutId,
                                          Map<String, Stanza> stanzePerRoomId,
                                          Set<String> importedStanzaNames) {
        String roomId = normalizeText(importedRoom.getId());
        String roomName = normalizeText(importedRoom.getLabel());
        String resolvedRoomName = resolveUniqueStanzaName(roomName, importedRoom, importedStanzaNames);
        String roomKey = normalizeStanzaName(resolvedRoomName);

        Stanza stanza = roomId != null ? stanzePerLayoutId.get(roomId) : null;
        if (stanza == null) {
            stanza = stanzePerNome.get(roomKey);
        }
        if (stanza == null) {
            stanza = new Stanza();
        }

        stanza.setNome(resolvedRoomName);
        stanza.setTipo(tipoStanza);
        stanza.setLayoutElementId(roomId);
        stanza.setXPct(importedRoom.getPosition() != null ? importedRoom.getPosition().getXPct() : null);
        stanza.setYPct(importedRoom.getPosition() != null ? importedRoom.getPosition().getYPct() : null);
        stanza.setPiano(piano);

        stanza = stanzaRepository.save(stanza);
        stanzePerNome.put(roomKey, stanza);
        if (roomId != null) {
            stanzePerLayoutId.put(roomId, stanza);
            stanzePerRoomId.put(roomId, stanza);
        }
        return stanza;
    }

    private Piano getPianoOrThrow(Long pianoId) {
        return pianoRepository.findById(pianoId)
                .orElseThrow(() -> new EntityNotFoundException("Piano non trovato con id: " + pianoId));
    }

    private Planimetria getByPianoIdOrThrow(Long pianoId) {
        return planimetriaRepository.findByPianoId(pianoId)
                .orElseThrow(() -> new EntityNotFoundException("Planimetria non trovata per il piano con id: " + pianoId));
    }

    private Path ensurePianoDirectory(Long pianoId) throws IOException {
        Path root = Paths.get(storageDir).toAbsolutePath().normalize();
        Path pianoDir = root.resolve("piano-" + pianoId);
        Files.createDirectories(pianoDir);
        return pianoDir;
    }

    private String buildBaseName(String originalFilename, Long pianoId) {
        String filename = (originalFilename == null || originalFilename.isBlank()) ? "planimetria" : originalFilename;
        int dotIndex = filename.lastIndexOf('.');
        String withoutExtension = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        String sanitized = withoutExtension.replaceAll("[^a-zA-Z0-9-_]", "-");
        return sanitized + "-" + pianoId + "-" + Instant.now().toEpochMilli();
    }

    private FormatoFile resolveFormato(String extension) {
        return switch (extension.toLowerCase()) {
            case "png" -> FormatoFile.PNG;
            case "jpg", "jpeg" -> FormatoFile.JPEG;
            case "svg" -> FormatoFile.SVG;
            case "dwg" -> FormatoFile.DWG;
            default -> FormatoFile.DXF;
        };
    }

    private String getExtension(String filename, String defaultExtension) {
        if (filename == null) {
            return defaultExtension;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return defaultExtension;
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private void writeMultipartFile(Path path, MultipartFile file) throws IOException {
        byte[] data = file.getBytes();
        if (data.length == 0) {
            throw new IOException("Contenuto file assente per " + path.getFileName());
        }
        Files.write(path, data);
    }

    private Optional<Postazione> findExistingPostazione(Long pianoId, String layoutElementId, String codice) {
        if (layoutElementId != null) {
            return postazioneRepository.findByLayoutElementIdAndStanzaPianoId(layoutElementId, pianoId);
        }
        if (codice != null) {
            return postazioneRepository.findByCodice(codice);
        }
        return Optional.empty();
    }

    private String resolveUniqueCodice(String codiceBase, Postazione current, Set<String> usedCodes) {
        String normalizedBase = normalizeText(codiceBase);
        Postazione existingWithSameCode = postazioneRepository.findByCodice(codiceBase).orElse(null);
        if (existingWithSameCode != null && current.getId() != null && current.getId().equals(existingWithSameCode.getId())) {
            usedCodes.add(normalizedBase);
            return codiceBase;
        }

        if (normalizedBase == null) {
            normalizedBase = "POSTAZIONE";
        }

        String candidate = normalizedBase;
        int suffix = 2;
        while (usedCodes.contains(normalizeText(candidate)) || postazioneRepository.findByCodice(candidate).isPresent()) {
            candidate = normalizedBase + "-" + suffix++;
        }

        usedCodes.add(normalizeText(candidate));
        return candidate;
    }

    private String resolveUniqueStanzaName(String roomName, PlanimetriaLayoutDto.RoomDto importedRoom, Set<String> usedNames) {
        String baseName = normalizeText(roomName);
        if (baseName == null) {
            baseName = importedRoom.getStations() != null && !importedRoom.getStations().isEmpty()
                    ? "Room"
                    : "Meeting Room";
        }
        String candidate = baseName;
        if (!usedNames.contains(normalizeStanzaName(candidate))) {
            usedNames.add(normalizeStanzaName(candidate));
            return candidate;
        }

        int suffix = 2;
        String resolved = candidate;
        while (usedNames.contains(normalizeStanzaName(resolved))) {
            resolved = candidate + " " + suffix++;
        }
        usedNames.add(normalizeStanzaName(resolved));
        return resolved;
    }

    private String normalizeStanzaName(String stanzaName) {
        String normalized = normalizeText(stanzaName);
        return normalized != null ? normalized.toLowerCase() : "senza nome";
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = normalizeText(first);
        return normalizedFirst != null ? normalizedFirst : normalizeText(second);
    }

    private void deleteIfReplaced(String oldPath, String newPath) {
        if (oldPath != null && !oldPath.equals(newPath)) {
            deleteQuietly(oldPath);
        }
    }

    private void deleteQuietly(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolvePath(pathString));
        } catch (IOException ignored) {
            // Evita che un problema di cleanup blocchi l'operazione principale.
        }
    }

    private Path resolveImagePath(Planimetria planimetria) {
        String preferredPath = firstNonBlank(planimetria.getImagePath(), planimetria.getFileOriginalePath());
        return preferredPath != null ? resolvePath(preferredPath) : null;
    }

    private Path resolvePath(String pathString) {
        return Paths.get(pathString).toAbsolutePath().normalize();
    }

    private String normalizePath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private PlanimetriaResponse toResponse(Planimetria planimetria) {
        Long pianoId = planimetria.getPiano().getId();
        return new PlanimetriaResponse(
                planimetria.getId(),
                pianoId,
                planimetria.getImageName(),
                planimetria.getFormatoOriginale(),
                "/api/piani/" + pianoId + "/planimetria/image",
                "/api/piani/" + pianoId + "/planimetria/postazioni",
                "/api/piani/" + pianoId + "/planimetria/layout"
        );
    }
}
