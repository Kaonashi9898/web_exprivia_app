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
 * Entità JPA che rappresenta una stanza nella tabella "stanza".
 *
 * Una stanza appartiene a un piano e contiene una o più postazioni.
 * Il vincolo di unicità su (nome, piano_id) garantisce che non possano
 * esistere due stanze con lo stesso nome sullo stesso piano.
 */
@Entity
@Table(
    name = "stanza",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_stanza_nome_piano", columnNames = {"nome", "piano_id"}),
        @UniqueConstraint(name = "uk_stanza_layout_element_piano", columnNames = {"layout_element_id", "piano_id"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Stanza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TipoStanza tipo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private StatoPostazione stato = StatoPostazione.DISPONIBILE;

    @Column(name = "layout_element_id")
    private String layoutElementId;

    @Column(name = "x_pct", precision = 6, scale = 3)
    private BigDecimal xPct;

    @Column(name = "y_pct", precision = 6, scale = 3)
    private BigDecimal yPct;

    // Piano a cui appartiene questa stanza
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piano_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Piano piano;

    // Postazioni contenute in questa stanza (eliminazione a cascata)
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Postazione> postazioni;
}
