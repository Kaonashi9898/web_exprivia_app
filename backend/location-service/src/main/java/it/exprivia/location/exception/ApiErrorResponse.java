package it.exprivia.location.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String timestamp,
        int status,
        String errore,
        Map<String, String> dettagli
) {

    public static ApiErrorResponse of(HttpStatus status, String errore) {
        return new ApiErrorResponse(Instant.now().toString(), status.value(), errore, null);
    }

    public static ApiErrorResponse of(HttpStatus status, String errore, Map<String, String> dettagli) {
        return new ApiErrorResponse(Instant.now().toString(), status.value(), errore, dettagli);
    }
}
