package it.exprivia.location.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
/**
 * Entità JPA che rappresenta la planimetria di un piano.
 *
 * Nel flusso attuale l'immagine della planimetria e il JSON esportato
 * dall'editor esterno vengono caricati separatamente.
 * Lo schema salva sia il file originale sia il file immagine da esporre al frontend.
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

    // Percorso dell'immagine visualizzata dal frontend
    @Column(name = "image_path")
    private String imagePath;

    // Percorso del file JSON esportato dall'editor esterno
    @Column(name = "json_path")
    private String jsonPath;

    // Nome del file immagine (usato per il recupero via URL)
    @Column(name = "image_name")
    private String imageName;

    // Formato del file immagine originale caricato
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "formato_originale")
    private FormatoFile formatoOriginale;

    // Piano a cui appartiene questa planimetria (relazione uno-a-uno bidirezionale)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piano_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Piano piano;
}
