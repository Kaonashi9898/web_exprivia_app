package it.exprivia.prenotazioni.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "prenotazione")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Prenotazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utente_id", nullable = false)
    private Long utenteId;

    @Column(name = "utente_email", nullable = false, length = 150)
    private String utenteEmail;

    @Column(name = "utente_full_name", nullable = false, length = 150)
    private String utenteFullName;

    @Column(name = "postazione_id")
    private Long postazioneId;

    @Column(name = "postazione_codice", length = 80)
    private String postazioneCodice;

    @Column(name = "meeting_room_stanza_id")
    private Long meetingRoomStanzaId;

    @Column(name = "meeting_room_nome", length = 120)
    private String meetingRoomNome;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo_risorsa_prenotata", nullable = false, columnDefinition = "tipo_risorsa_prenotata")
    private TipoRisorsaPrenotata tipoRisorsaPrenotata;

    @Column(name = "stanza_id", nullable = false)
    private Long stanzaId;

    @Column(name = "stanza_nome", nullable = false, length = 120)
    private String stanzaNome;

    @Column(name = "data_prenotazione", nullable = false)
    private LocalDate dataPrenotazione;

    @Column(name = "ora_inizio", nullable = false)
    private LocalTime oraInizio;

    @Column(name = "ora_fine", nullable = false)
    private LocalTime oraFine;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "stato_prenotazione")
    private StatoPrenotazione stato;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
