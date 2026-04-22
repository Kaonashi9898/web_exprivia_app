package it.exprivia.location.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Entità JPA che rappresenta un edificio nella tabella "edificio".
 *
 * Un edificio appartiene a una sede (@ManyToOne) e contiene uno o più piani (@OneToMany).
 * La cancellazione a cascata (CascadeType.ALL) garantisce che eliminando un edificio
 * vengano eliminati automaticamente anche tutti i suoi piani.
 *
 * FetchType.LAZY su sede significa che la sede NON viene caricata immediatamente
 * dal DB, ma solo quando effettivamente acceduta (ottimizzazione delle performance).
 */
@Entity
@Table(name = "edificio")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Edificio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nome;

    // Relazione molti-a-uno con Sede: molti edifici possono appartenere a una sede
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id", nullable = false)
    private Sede sede;

    // Relazione uno-a-molti con Piano: un edificio ha molti piani
    @OneToMany(mappedBy = "edificio", cascade = CascadeType.ALL)
    private List<Piano> piani;
}
