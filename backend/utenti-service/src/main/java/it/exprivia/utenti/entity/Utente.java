package it.exprivia.utenti.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Entità JPA che rappresenta un utente registrato nel sistema.
 *
 * Corrisponde alla tabella "utenti" nel database PostgreSQL.
 * La password non viene mai salvata in chiaro: viene memorizzato solo
 * l'hash BCrypt nel campo passwordHash.
 *
 * @JdbcTypeCode(SqlTypes.NAMED_ENUM) indica a Hibernate di trattare
 * il ruolo come un tipo ENUM nativo di PostgreSQL (non come VARCHAR).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "utenti")
public class Utente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name= "full_name")
    @NotNull
    @Size(max= 50)
    private String fullName;

    @NotNull
    @Size(max= 50)
    private String email;

    // Hash BCrypt della password — non è mai la password in chiaro
    @Column(name= "password_hash")
    @Size(max= 120)
    private String passwordHash;

    // Il ruolo è mappato come tipo ENUM nativo di PostgreSQL
    @Column(name = "ruolo")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @NotNull
    private RuoloUtente ruolo;

}
