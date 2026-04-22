package it.exprivia.location.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entità JPA che rappresenta una postazione di lavoro prenotabile.
 *
 * È l'unità atomica del sistema di prenotazioni. Una postazione ha:
 * - un codice univoco (es. "A1-03") per identificarla
 * - un cadId opzionale che la collega a un oggetto nel disegno CAD
 * - un tipo (scrivania, sala riunioni, ecc.)
 * - uno stato corrente (disponibile, manutenzione, ecc.)
 * - coordinate x/y nella planimetria CAD per la visualizzazione sulla mappa
 * - un flag "accessibile" per le postazioni con accesso disabili
 */
@Entity
@Table(name = "postazione")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Postazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Codice univoco della postazione (es. "A1-DESK-03"), usato per la ricerca rapida
    @NotBlank
    @Column(nullable = false, unique = true)
    private String codice;

    // Identificativo dell'oggetto nel disegno CAD — opzionale, usato per la planimetria interattiva
    @Column(name = "cad_id")
    private String cadId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TipoPostazione tipo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private StatoPostazione stato;

    // Indica se la postazione è accessibile a persone con disabilità motorie
    @Column(nullable = false)
    private Boolean accessibile = Boolean.FALSE;
    
    // Coordinata X nella planimetria CAD (usata per posizionare il marker sulla mappa)
    @Column(precision = 12, scale = 2)
    private BigDecimal x;

    // Coordinata Y nella planimetria CAD
    @Column(precision = 12, scale = 2)
    private BigDecimal y;

    // Stanza a cui appartiene questa postazione
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;

    // Lista dei gruppi che hanno accesso riservato a questa postazione
    @OneToMany(mappedBy = "postazione", cascade = CascadeType.ALL)
    private List<GruppoPostazione> gruppiPostazione;
}
