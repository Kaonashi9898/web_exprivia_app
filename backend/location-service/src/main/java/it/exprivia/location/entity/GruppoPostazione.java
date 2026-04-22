package it.exprivia.location.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entità JPA che rappresenta l'associazione tra un gruppo utenti e una postazione.
 *
 * Corrisponde alla tabella "gruppo_postazione". Serve per indicare
 * che una postazione è riservata/accessibile a un determinato gruppo.
 *
 * Il vincolo di unicità su (gruppo_id, postazione_id) impedisce duplicati.
 *
 * Nota architetturale: gruppoId è un riferimento logico all'ID del gruppo in
 * utenti-service. Non esiste una foreign key fisica perché i due servizi
 * hanno database separati (architettura a microservizi).
 */
@Entity
@Table(
    name = "gruppo_postazione",
    uniqueConstraints = @UniqueConstraint(name = "uk_gruppo_postazione", columnNames = {"gruppo_id", "postazione_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GruppoPostazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID del gruppo in utenti-service — riferimento logico, nessuna FK cross-DB
    @NotNull
    @Column(name = "gruppo_id", nullable = false)
    private Long gruppoId;

    // Relazione con la postazione (FK reale, stessa DB)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postazione_id", nullable = false)
    private Postazione postazione;
}
