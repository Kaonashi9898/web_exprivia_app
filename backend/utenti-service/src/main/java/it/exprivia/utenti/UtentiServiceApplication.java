package it.exprivia.utenti;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principale del microservizio utenti.
 *
 * Questa classe avvia l'applicazione Spring Boot. L'annotazione
 * @SpringBootApplication abilita la configurazione automatica, la scansione
 * dei componenti e la configurazione dei bean.
 *
 * Questo servizio gestisce: registrazione, login (con JWT), gestione utenti
 * e gestione gruppi.
 */
@SpringBootApplication
public class UtentiServiceApplication {

	public static void main(String[] args) {
		// Avvia l'applicazione Spring Boot passando la classe di configurazione
		SpringApplication.run(UtentiServiceApplication.class, args);
	}

}
