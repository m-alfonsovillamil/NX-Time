package com.nxtime.nxtime.domain;

/**
 * En qué punto está una candidatura (Fase H).
 *
 * {@link #RECIBIDA} es el único estado sin resolutor: en cuanto alguien
 * la mueve, queda quién y cuándo, como en las ausencias (V4), las
 * correcciones (V11) y las denuncias (V13).
 *
 * <b>{@link #DESCARTADA} no es un estado técnico.</b> Es lo que un
 * compañero va a leer sobre sí mismo, así que mover una candidatura ahí
 * pide comentario: decirle a alguien de la casa que no sigue adelante
 * sin una palabra más es la peor forma de usar un canal de promoción
 * interna.
 */
public enum ApplicationStatus {

    /** Presentada y sin mirar todavía. */
    RECIBIDA,

    /** En valoración. */
    EN_PROCESO,

    /** No sigue adelante. Siempre con comentario. */
    DESCARTADA,

    /** La persona elegida para el puesto. */
    SELECCIONADA;

    /** Si sigue viva para quien se presentó. */
    public boolean estaViva() {
        return this == RECIBIDA || this == EN_PROCESO;
    }

    /** Si cierra la candidatura, en un sentido o en el otro. */
    public boolean esFinal() {
        return this == DESCARTADA || this == SELECCIONADA;
    }
}
