package it.exprivia.utenti.dto;

import it.exprivia.utenti.entity.RuoloUtente;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO (Data Transfer Object) che rappresenta un utente nelle risposte API.
 *
 * Viene usato per trasferire i dati dell'utente al client senza esporre
 * campi sensibili come la password hashata. Contiene solo le informazioni
 * necessarie per la visualizzazione e la gestione lato frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UtenteDTO {

    private Long id;
    private String fullName;
    private String email;
    private RuoloUtente ruolo;
}