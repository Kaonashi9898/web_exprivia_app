package it.exprivia.location.entity;

/**
 * Enumerazione degli stati possibili per una postazione.
 *
 * - DISPONIBILE        → la postazione può essere prenotata
 * - NON_DISPONIBILE    → la postazione non può essere prenotata (es. assegnata a un utente fisso)
 * - MANUTENZIONE       → la postazione è temporaneamente fuori uso per manutenzione
 * - CAMBIO_DESTINAZIONE → la postazione sta cambiando tipo/utilizzo
 */
public enum StatoPostazione {
    DISPONIBILE,
    NON_DISPONIBILE,
    MANUTENZIONE,
    CAMBIO_DESTINAZIONE;

    /**
     * Metodo di utilità per convertire un booleano in stato postazione.
     * true → DISPONIBILE, false → NON_DISPONIBILE
     */
    public static StatoPostazione fromDisponibilita(boolean disponibilita) {
        return disponibilita ? DISPONIBILE : NON_DISPONIBILE;
    }
}
