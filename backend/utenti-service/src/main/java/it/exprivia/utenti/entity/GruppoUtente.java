package it.exprivia.utenti.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entità JPA che rappresenta la relazione molti-a-molti tra Gruppo e Utente.
 *
 * Corrisponde alla tabella di join "gruppi_utente" nel database.
 * Ogni riga indica che un certo utente (idUtente) appartiene a un certo gruppo (idGruppo).
 * Questa è una tecnica comune per gestire relazioni N:M senza usare @ManyToMany,
 * mantenendo più controllo sulla tabella di join.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "gruppi_utente")
public class GruppoUtente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Chiave esterna verso la tabella "gruppi"
    @Column(name= "id_gruppo")
    private Long idGruppo;

    // Chiave esterna verso la tabella "utenti"
    @Column(name= "id_utente")
    private Long idUtente;


    
}
