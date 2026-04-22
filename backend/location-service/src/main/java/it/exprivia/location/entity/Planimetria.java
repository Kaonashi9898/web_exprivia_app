package it.exprivia.location.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Entità JPA che rappresenta la planimetria di un piano.
 *
 * Nel flusso attuale l'immagine della planimetria e il JSON esportato
 * dall'editor esterno vengono caricati separatamente.
 *
 * I campi coord* contengono l'intervallo logico delle coordinate della mappa.
 * Con il nuovo editor le coordinate sono percentuali rispetto all'immagine,
 * quindi il range atteso è 0..100.
 * Un piano può avere al massimo una planimetria (@OneToOne, unique = true).
 */
@Entity
@Table(name = "planimetria")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Planimetria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Percorso del file immagine originale caricato dall'utente
    @Column(name = "file_originale_path")
    private String fileOriginalePath;

    // Percorso dell'immagine visualizzata dal frontend.
    // Per retrocompatibilità continua a usare la colonna png_path.
    @Column(name = "png_path")
    private String pngPath;

    // Percorso del file JSON esportato dall'editor esterno
    @Column(name = "json_path")
    private String jsonPath;

    // Nome del file immagine (usato per il recupero via URL)
    @Column(name = "image_name")
    private String imageName;

    // Coordinate del bounding box del disegno CAD (usate per mappare coord CAD ↔ pixel)
    @Column(name = "coord_xmin", precision = 12, scale = 2)
    private BigDecimal coordXmin;

    @Column(name = "coord_xmax", precision = 12, scale = 2)
    private BigDecimal coordXmax;

    @Column(name = "coord_ymin", precision = 12, scale = 2)
    private BigDecimal coordYmin;

    @Column(name = "coord_ymax", precision = 12, scale = 2)
    private BigDecimal coordYmax;

    // Formato del file immagine originale caricato
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "formato_originale")
    private FormatoFile formatoOriginale;

    // Piano a cui appartiene questa planimetria (relazione uno-a-uno bidirezionale)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piano_id", nullable = false, unique = true)
    private Piano piano;
}
