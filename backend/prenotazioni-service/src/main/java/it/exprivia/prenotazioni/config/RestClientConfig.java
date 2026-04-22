package it.exprivia.prenotazioni.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;


@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Qualifier("utentiRestClient")
    public RestClient utentiRestClient(RestClient.Builder builder,
                                       @Value("${services.utenti.base-url}") String utentiBaseUrl) {
        return builder
                .baseUrl(utentiBaseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @Qualifier("locationRestClient")
    public RestClient locationRestClient(RestClient.Builder builder,
                                         @Value("${services.location.base-url}") String locationBaseUrl) {
        return builder
                .baseUrl(locationBaseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
