package it.exprivia.location.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    uniqueConstraints = @UniqueConstraint(name = "uk_stanza_nome_piano", columnNames = {"nome", "piano_id"})
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

    // Piano a cui appartiene questa stanza
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piano_id", nullable = false)
    private Piano piano;

    // Postazioni contenute in questa stanza (eliminazione a cascata)
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL)
    private List<Postazione> postazioni;
}
