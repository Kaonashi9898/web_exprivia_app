package it.exprivia.location;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principale del microservizio location.
 *
 * Questo servizio gestisce la gerarchia fisica degli spazi aziendali:
 * Sede → Edificio → Piano → Stanza → Postazione
 *
 * Gestisce anche:
 * - Planimetrie (file DXF/DWG caricati e convertiti in PNG)
 * - Gruppi postazione (associazione tra gruppi utenti e postazioni riservate)
 * - Ascolto di eventi RabbitMQ da utenti-service per la pulizia dei dati
 */
@SpringBootApplication
public class LocationServiceApplication {

	public static void main(String[] args) {
		// Avvia l'applicazione Spring Boot
		SpringApplication.run(LocationServiceApplication.class, args);
	}

}
