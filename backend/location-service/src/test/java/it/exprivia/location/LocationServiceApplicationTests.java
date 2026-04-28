package it.exprivia.location;

import it.exprivia.location.repository.EdificioRepository;
import it.exprivia.location.repository.GruppoPostazioneRepository;
import it.exprivia.location.repository.PianoRepository;
import it.exprivia.location.repository.PlanimetriaRepository;
import it.exprivia.location.repository.PostazioneRepository;
import it.exprivia.location.repository.SedeRepository;
import it.exprivia.location.repository.StanzaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class LocationServiceApplicationTests {

    @MockitoBean
    SedeRepository sedeRepository;

    @MockitoBean
    EdificioRepository edificioRepository;

    @MockitoBean
    PianoRepository pianoRepository;

    @MockitoBean
    StanzaRepository stanzaRepository;

    @MockitoBean
    PostazioneRepository postazioneRepository;

    @MockitoBean
    PlanimetriaRepository planimetriaRepository;

    @MockitoBean
    GruppoPostazioneRepository gruppoPostazioneRepository;

    @MockitoBean
    ConnectionFactory connectionFactory;

    @Test
    void contextLoads() {
    }
}
