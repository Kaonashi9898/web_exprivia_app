package it.exprivia.location.entity;

/**
 * Enumerazione dei tipi di postazione disponibili nel sistema.
 *
 * - OPEN_SPACE      → scrivania in area aperta condivisa
 * - SALA_RIUNIONI   → spazio per meeting (capacità variabile)
 * - UFFICIO_PRIVATO → ufficio singolo o a uso esclusivo
 * - LABORATORIO     → spazio tecnico attrezzato
 */
public enum TipoPostazione {
    OPEN_SPACE,
    SALA_RIUNIONI,
    UFFICIO_PRIVATO,
    LABORATORIO
}
