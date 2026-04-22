package it.exprivia.location.entity;

/**
 * Enumerazione dei formati supportati per i file di planimetria.
 *
 * - PNG / JPEG / SVG: immagini caricate direttamente dall'admin o building manager
 * - DWG / DXF: formati legacy mantenuti per compatibilità con dati già presenti
 */
public enum FormatoFile {
    PNG,
    JPEG,
    SVG,
    DWG,
    DXF
}
