package it.exprivia.utentiservice;

import it.exprivia.utenti.UtentiServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.exprivia.utenti.repository.GruppoRepository;
import it.exprivia.utenti.repository.GruppoUtenteRepository;
import it.exprivia.utenti.repository.UtenteRepository;

@SpringBootTest(classes = UtentiServiceApplication.class)
@ActiveProfiles("test")
class UtentiServiceApplicationTests {

	@MockitoBean
	UtenteRepository utenteRepository;

	@MockitoBean
	GruppoRepository gruppoRepository;

	@MockitoBean
	GruppoUtenteRepository gruppoUtenteRepository;

	@MockitoBean
	ConnectionFactory connectionFactory;

	@Test
	void contextLoads() {
	}

}
