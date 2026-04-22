package it.exprivia.location.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanimetriaLayoutDto {

    private String exportedAt;
    private ImageDto image;
    private List<RoomDto> rooms;
    private List<RoomDto> meetings;
    private List<StationDto> stations;
    private List<ConnectionDto> connections;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageDto {
        private String filename;
        private Integer naturalWidth;
        private Integer naturalHeight;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomDto {
        private String id;
        private String label;
        private PositionDto position;
        private List<String> stationIds;
        private List<StationDto> stations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StationDto {
        private String id;
        private String label;
        private PositionDto position;
        private String roomId;
        private String roomLabel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionDto {
        private String stationId;
        private String roomId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionDto {
        @JsonProperty("xPct")
        private BigDecimal xPct;
        @JsonProperty("yPct")
        private BigDecimal yPct;
    }
}
