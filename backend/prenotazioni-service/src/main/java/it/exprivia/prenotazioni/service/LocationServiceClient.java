package it.exprivia.prenotazioni.service;
import it.exprivia.prenotazioni.dto.ExternalGruppoPostazioneResponse;

import it.exprivia.prenotazioni.dto.ExternalPostazioneResponse;
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
public class LocationServiceClient {

    private final RestClient restClient;

    public LocationServiceClient(@Qualifier("locationRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ExternalPostazioneResponse getPostazione(Long postazioneId, String authorizationHeader) {
        try {
            ExternalPostazioneResponse response = restClient.get()
                    .uri("/api/postazioni/{id}", postazioneId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(ExternalPostazioneResponse.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Risposta vuota da location-service");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapException(ex, postazioneId);
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "location-service non raggiungibile");
        }
    }

    public List<Long> getGruppiAbilitati(Long postazioneId, String authorizationHeader) {
        try {
            ExternalGruppoPostazioneResponse[] response = restClient.get()
                    .uri("/api/gruppi-postazioni/postazione/{id}", postazioneId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(ExternalGruppoPostazioneResponse[].class);
            if (response == null) {
                return List.of();
            }
            return Arrays.stream(response)
                    .map(ExternalGruppoPostazioneResponse::gruppoId)
                    .toList();
        } catch (RestClientResponseException ex) {
            throw mapException(ex, postazioneId);
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "location-service non raggiungibile");
        }
    }

    private ResponseStatusException mapException(RestClientResponseException ex, Long postazioneId) {
        int status = ex.getStatusCode().value();
        if (status == 404) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Postazione non trovata con id: " + postazioneId);
        }
        if (status == 401 || status == 403) {
            return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token non valido per location-service");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Errore nella comunicazione con location-service");
    }
}
