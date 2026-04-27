package it.exprivia.location.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/**
 * Entità JPA che rappresenta un piano di un edificio nella tabella "piano".
 *
 * Un piano ha un numero progressivo (0 = piano terra), appartiene a un edificio
 * e contiene stanze. Può anche avere una planimetria associata (relazione @OneToOne).
 *
 * La cascata ALL garantisce che eliminando un piano vengano eliminati
 * automaticamente anche le stanze e la planimetria correlate.
 */
@Entity
@Table(name = "piano")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Piano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Numero del piano (es. 0 = piano terra, 1 = primo piano, -1 = interrato)
    @NotNull
    @Column(nullable = false)
    private Integer numero;

    @Column(length = 100)
    private String nome;

    // Edificio a cui appartiene questo piano
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edificio_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Edificio edificio;

    // Stanze presenti in questo piano
    @OneToMany(mappedBy = "piano", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Stanza> stanze;

    // Planimetria del piano (opzionale — può non essere caricata)
    @OneToOne(mappedBy = "piano", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Planimetria planimetria;
}
