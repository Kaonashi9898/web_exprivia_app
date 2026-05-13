package it.exprivia.prenotazioni.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prenotazione_notifica")
public class PrenotazioneNotifica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utente_id", nullable = false)
    @NotNull
    private Long utenteId;

    @Column(name = "prenotazione_id", nullable = false)
    @NotNull
    private Long prenotazioneId;

    @Column(nullable = false, columnDefinition = "motivo_notifica_prenotazione")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @NotNull
    private MotivoNotificaPrenotazione motivo;

    @Column(name = "risorsa_label", nullable = false, length = 120)
    @NotNull
    @Size(max = 120)
    private String risorsaLabel;

    @Column(name = "stanza_nome", nullable = false, length = 120)
    @NotNull
    @Size(max = 120)
    private String stanzaNome;

    @Column(name = "data_prenotazione", nullable = false)
    @NotNull
    private LocalDate dataPrenotazione;

    @Column(name = "ora_inizio", nullable = false)
    @NotNull
    private LocalTime oraInizio;

    @Column(name = "ora_fine", nullable = false)
    @NotNull
    private LocalTime oraFine;

    @Column(name = "stato_postazione", nullable = false, length = 40)
    @NotNull
    @Size(max = 40)
    private String statoPostazione;

    @Column(name = "created_at", nullable = false)
    @NotNull
    private OffsetDateTime createdAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;
}
