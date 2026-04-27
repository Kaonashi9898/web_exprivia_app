package it.exprivia.location.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.List;

/**
 * Entità JPA che rappresenta una postazione di lavoro prenotabile.
 *
 * È l'unità atomica del sistema di prenotazioni. Una postazione ha:
 * - un codice univoco (es. "A1-03") per identificarla
 * - un layoutElementId opzionale che la collega a una station del layout
 * - uno stato corrente (disponibile, manutenzione, ecc.)
 * - coordinate percentuali x/y sulla planimetria per la visualizzazione sulla mappa
 */
@Entity
@Table(
    name = "postazione",
    uniqueConstraints = @UniqueConstraint(name = "uk_postazione_layout_element_stanza",
            columnNames = {"layout_element_id", "stanza_id"})
)
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

    // Identificativo della station nel layout esportato dall'editor
    @Column(name = "layout_element_id")
    private String layoutElementId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private StatoPostazione stato;
    
    // Coordinata X in percentuale sulla planimetria
    @Column(name = "x_pct", precision = 6, scale = 3)
    private BigDecimal xPct;

    // Coordinata Y in percentuale sulla planimetria
    @Column(name = "y_pct", precision = 6, scale = 3)
    private BigDecimal yPct;

    // Stanza a cui appartiene questa postazione
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Stanza stanza;

    // Lista dei gruppi che hanno accesso riservato a questa postazione
    @OneToMany(mappedBy = "postazione", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<GruppoPostazione> gruppiPostazione;
}
