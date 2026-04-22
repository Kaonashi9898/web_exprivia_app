package it.exprivia.utenti.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entità JPA che rappresenta un gruppo di utenti nella tabella "gruppi".
 *
 * I gruppi vengono usati per organizzare gli utenti (es. reparti o team).
 * La relazione tra gruppi e utenti è gestita tramite la tabella di join
 * {@link GruppoUtente}.
 */
@Table(name = "gruppi")
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Gruppo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max= 50)
    @NotNull
    private String nome;

}
