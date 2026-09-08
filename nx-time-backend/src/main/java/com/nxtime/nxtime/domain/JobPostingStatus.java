package com.nxtime.nxtime.domain;

/**
 * En qué punto está una oferta interna (Fase H).
 *
 * <b>{@link #ABIERTA} no significa que admita candidaturas.</b> Eso
 * depende además de la fecha de cierre, y se resuelve al leer — igual
 * que los plazos de las denuncias o la bolsa de horas extra. Una oferta
 * cuyo plazo venció ayer sigue siendo {@code ABIERTA} en la base hasta
 * que alguien la cierre, y aun así no acepta a nadie: quien lo decide es
 * {@code JobPostingService}, no esta columna.
 *
 * No hay proceso nocturno que las cierre. Un estado que cambia solo
 * obliga a mirar cuándo corrió el proceso por última vez para saber si
 * lo que se ve es verdad; derivarlo al leer no puede quedarse atrás.
 */
public enum JobPostingStatus {

    /** Escrita y todavía sin publicar. Solo la ve quien la escribe. */
    BORRADOR,

    /** Publicada. La ve toda la empresa. */
    ABIERTA,

    /** Cerrada a mano. No admite candidaturas, y las que hay siguen ahí. */
    CERRADA;

    /** Si está publicada, con independencia de su fecha de cierre. */
    public boolean estaPublicada() {
        return this == ABIERTA;
    }
}
