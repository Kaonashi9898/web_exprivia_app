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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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

    private static final BigDecimal PERCENT_MIN = BigDecimal.ZERO;
    private static final BigDecimal PERCENT_MAX = new BigDecimal("100");
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
        validateImageFile(file);

        Piano piano = getPianoOrThrow(pianoId);
        try {
            Path pianoDir = ensurePianoDirectory(pianoId);
            String extension = getExtension(file.getOriginalFilename(), "png");
            String baseName = buildBaseName(file.getOriginalFilename(), pianoId);
            Path imagePath = pianoDir.resolve(baseName + "." + extension);
            writeMultipartFile(imagePath, file);

            Planimetria existing = planimetriaRepository.findByPianoId(pianoId).orElse(null);
            String oldOriginalPath = existing != null ? existing.getFileOriginalePath() : null;
            String oldImagePath = existing != null ? existing.getPngPath() : null;

            Planimetria planimetria = existing != null ? existing : new Planimetria();
            planimetria.setPiano(piano);
            planimetria.setFileOriginalePath(normalizePath(imagePath));
            planimetria.setPngPath(normalizePath(imagePath));
            planimetria.setImageName(imagePath.getFileName().toString());
            planimetria.setFormatoOriginale(resolveFormato(extension));
            if (existing == null) {
                piano.setPlanimetria(planimetria);
            }

            Planimetria saved = planimetriaRepository.save(planimetria);
            piano.setNome(resolveDisplayName(file.getOriginalFilename()));
            deleteIfReplaced(oldOriginalPath, saved.getFileOriginalePath());
            deleteIfReplaced(oldImagePath, saved.getPngPath());
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
            applyPercentBounds(planimetria, layout);
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
        Map<String, Stanza> stanzePerNome = new HashMap<>();
        for (Stanza stanza : stanzaRepository.findByPianoId(piano.getId())) {
            stanzePerNome.put(normalizeStanzaName(stanza.getNome()), stanza);
        }

        Map<String, Stanza> stanzePerRoomId = new HashMap<>();
        for (PlanimetriaLayoutDto.RoomDto importedRoom : safeList(layout.getRooms())) {
            String roomName = normalizeText(importedRoom.getLabel());
            String resolvedRoomName = roomName != null ? roomName : "Senza nome";
            String roomKey = normalizeStanzaName(resolvedRoomName);
            Stanza stanza = stanzePerNome.get(roomKey);
            if (stanza == null) {
                stanza = new Stanza();
                stanza.setNome(resolvedRoomName);
                stanza.setPiano(piano);
                stanza = stanzaRepository.save(stanza);
                stanzePerNome.put(roomKey, stanza);
            }
            stanzePerRoomId.put(importedRoom.getId(), stanza);
        }

        Set<String> importedStationIds = new HashSet<>();
        Set<String> importedCodes = new HashSet<>();
        for (PlanimetriaLayoutDto.StationDto importedStation : safeList(layout.getStations())) {
            String cadId = normalizeText(importedStation.getId());
            String codiceBase = normalizeText(importedStation.getLabel());
            String codice = codiceBase != null ? codiceBase : cadId;
            if (codice == null) {
                continue;
            }

            importedStationIds.add(cadId);
            importedCodes.add(codice);

            Stanza stanza = stanzePerRoomId.get(importedStation.getRoomId());
            if (stanza == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La postazione " + firstNonBlank(importedStation.getLabel(), importedStation.getId())
                                + " fa riferimento a una stanza inesistente");
            }

            Postazione postazione = findExistingPostazione(piano.getId(), cadId, codice)
                    .orElseGet(Postazione::new);

            if (postazione.getTipo() == null) {
                postazione.setTipo(inferTipoPostazione(stanza.getNome()));
            }
            if (postazione.getStato() == null) {
                postazione.setStato(StatoPostazione.DISPONIBILE);
            }
            if (postazione.getAccessibile() == null) {
                postazione.setAccessibile(Boolean.FALSE);
            }

            postazione.setCodice(resolveUniqueCodice(codice, postazione, piano.getId(), cadId));
            postazione.setCadId(cadId);
            postazione.setX(importedStation.getPosition() != null ? importedStation.getPosition().getXPct() : null);
            postazione.setY(importedStation.getPosition() != null ? importedStation.getPosition().getYPct() : null);
            postazione.setStanza(stanza);
            postazioneRepository.save(postazione);
        }

        // Evita di lasciare postazioni tecniche duplicate create da import precedenti con id/codice non più presenti.
        for (Postazione existing : postazioneRepository.findByStanzaPianoId(piano.getId())) {
            String existingCadId = normalizeText(existing.getCadId());
            String existingCode = normalizeText(existing.getCodice());
            if (existingCadId != null && !importedStationIds.contains(existingCadId)
                    && importedCodes.contains(existingCode)) {
                existing.setCadId(null);
                postazioneRepository.save(existing);
            }
        }
    }

    @Transactional
    public void deleteByPianoId(Long pianoId) {
        Planimetria planimetria = getByPianoIdOrThrow(pianoId);
        Piano piano = planimetria.getPiano();
        List<Long> postazioneIds = postazioneRepository.findByStanzaPianoId(pianoId).stream()
                .map(Postazione::getId)
                .toList();
        if (piano != null) {
            piano.setPlanimetria(null);
            piano.setNome(null);
        }
        planimetriaRepository.delete(planimetria);
        deleteQuietly(planimetria.getFileOriginalePath());
        deleteQuietly(planimetria.getPngPath());
        deleteQuietly(planimetria.getJsonPath());
        planimetriaEventPublisher.pubblicaEliminazione(new PlanimetriaEliminataEvent(pianoId, postazioneIds));
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
        if (!safeList(layout.getMeetings()).isEmpty()) {
            List<PlanimetriaLayoutDto.RoomDto> roomsWithMeetings = new java.util.ArrayList<>(safeList(layout.getRooms()));
            roomsWithMeetings.addAll(layout.getMeetings());
            layout.setRooms(roomsWithMeetings);
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

    private String resolveDisplayName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        String filename = Paths.get(originalFilename).getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        String withoutExtension = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        String normalized = withoutExtension.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
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

    private void applyPercentBounds(Planimetria planimetria, PlanimetriaLayoutDto layout) {
        boolean hasMarkers = !safeList(layout.getRooms()).isEmpty() || !safeList(layout.getStations()).isEmpty();
        if (!hasMarkers) {
            planimetria.setCoordXmin(null);
            planimetria.setCoordXmax(null);
            planimetria.setCoordYmin(null);
            planimetria.setCoordYmax(null);
            return;
        }
        planimetria.setCoordXmin(PERCENT_MIN);
        planimetria.setCoordXmax(PERCENT_MAX);
        planimetria.setCoordYmin(PERCENT_MIN);
        planimetria.setCoordYmax(PERCENT_MAX);
    }

    private Optional<Postazione> findExistingPostazione(Long pianoId, String cadId, String codice) {
        if (cadId != null) {
            Optional<Postazione> byCadId = postazioneRepository.findByCadIdAndStanzaPianoId(cadId, pianoId);
            if (byCadId.isPresent()) {
                return byCadId;
            }
        }
        if (codice != null) {
            return postazioneRepository.findByCodice(codice);
        }
        return Optional.empty();
    }

    private String resolveUniqueCodice(String codiceBase, Postazione current, Long pianoId, String cadId) {
        Postazione existingWithSameCode = postazioneRepository.findByCodice(codiceBase).orElse(null);
        if (existingWithSameCode == null) {
            return codiceBase;
        }
        if (current.getId() != null && current.getId().equals(existingWithSameCode.getId())) {
            return codiceBase;
        }
        if (current.getCodice() != null && !current.getCodice().isBlank()) {
            return current.getCodice();
        }

        String suffixSource = cadId != null ? cadId : String.valueOf(Instant.now().toEpochMilli());
        return codiceBase + "-" + pianoId + "-" + Math.abs(suffixSource.hashCode());
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

    private TipoPostazione inferTipoPostazione(String stanzaName) {
        String normalized = normalizeStanzaName(stanzaName);
        if (normalized.contains("riunion") || normalized.contains("meeting") || normalized.contains("sala")) {
            return TipoPostazione.SALA_RIUNIONI;
        }
        if (normalized.contains("labor")) {
            return TipoPostazione.LABORATORIO;
        }
        if (normalized.contains("ufficio") || normalized.contains("office")) {
            return TipoPostazione.UFFICIO_PRIVATO;
        }
        return TipoPostazione.OPEN_SPACE;
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
        String preferredPath = firstNonBlank(planimetria.getFileOriginalePath(), planimetria.getPngPath());
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
                planimetria.getCoordXmin(),
                planimetria.getCoordXmax(),
                planimetria.getCoordYmin(),
                planimetria.getCoordYmax(),
                "/api/piani/" + pianoId + "/planimetria/image",
                "/api/piani/" + pianoId + "/planimetria/postazioni",
                "/api/piani/" + pianoId + "/planimetria/layout"
        );
    }
}
