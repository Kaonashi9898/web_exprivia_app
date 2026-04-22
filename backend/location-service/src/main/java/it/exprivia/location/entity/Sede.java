package it.exprivia.location.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entità JPA che rappresenta una sede aziendale nella tabella "sede".
 *
 * È la radice della gerarchia fisica: Sede → Edificio → Piano → Stanza → Postazione.
 * Le coordinate geografiche (latitudine e longitudine) permettono di mostrare
 * le sedi su una mappa nel frontend.
 */
@Entity
@Table(name = "sede")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sede {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nome;

    @NotBlank
    @Column(nullable = false)
    private String indirizzo;

    @NotBlank
    @Column(nullable = false)
    private String citta;

    // Coordinate geografiche per la visualizzazione su mappa (opzionali)
    @Column(precision = 9, scale = 6)
    private BigDecimal latitudine;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitudine;

    // Lista degli edifici della sede (eliminazione a cascata)
    @OneToMany(mappedBy = "sede", cascade = CascadeType.ALL)
    private List<Edificio> edifici;
}
