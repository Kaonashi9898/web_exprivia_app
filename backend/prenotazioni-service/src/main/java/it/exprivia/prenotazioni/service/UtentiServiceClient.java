package it.exprivia.prenotazioni.service;

import it.exprivia.prenotazioni.dto.ExternalUtenteResponse;
import it.exprivia.prenotazioni.dto.ExternalGruppoResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@Component
public class UtentiServiceClient {

    private final RestClient restClient;

    public UtentiServiceClient(@Qualifier("utentiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ExternalUtenteResponse getCurrentUser(String authorizationHeader) {
        try {
            ExternalUtenteResponse response = restClient.get()
                    .uri("/api/utenti/me")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(ExternalUtenteResponse.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Risposta vuota da utenti-service");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapException("utenti-service", ex);
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "utenti-service non raggiungibile");
        }
    }

    public List<Long> getCurrentUserGroupIds(String authorizationHeader) {
        try {
            ExternalGruppoResponse[] response = restClient.get()
                    .uri("/api/gruppi/me")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(ExternalGruppoResponse[].class);
            if (response == null) {
                return List.of();
            }
            return Arrays.stream(response)
                    .map(ExternalGruppoResponse::id)
                    .toList();
        } catch (RestClientResponseException ex) {
            throw mapException("utenti-service", ex);
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "utenti-service non raggiungibile");
        }
    }

    private ResponseStatusException mapException(String serviceName, RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 404) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Utente autenticato non trovato");
        }
        if (status == 401 || status == 403) {
            return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token non valido per " + serviceName);
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Errore nella comunicazione con " + serviceName);
    }
}
