package com.nxtime.nxtime.domain;

/**
 * En qué punto está una denuncia (Fase G).
 *
 * Los dos primeros están ABIERTOS: el expediente sigue vivo y los
 * plazos del art. 9.2 de la Ley 2/2023 siguen corriendo. Los dos
 * últimos lo cierran, y los dos exigen conclusión escrita.
 *
 * <b>{@link #ARCHIVADA} no es "rechazada".</b> Es el desenlace de una
 * denuncia que se investigó y no se sostuvo, o que quedaba fuera del
 * ámbito del canal. Se le sigue debiendo una respuesta a quien la
 * presentó, y por eso lleva conclusión igual que {@link #RESUELTA}: la
 * ley obliga a responder, no a dar la razón.
 */
public enum ComplaintStatus {

    /** Presentada y todavía sin tocar por quien instruye. */
    RECIBIDA,

    /** Con acuse de recibo dado y en curso. */
    EN_INVESTIGACION,

    /** Cerrada habiéndose confirmado, con la conclusión escrita. */
    RESUELTA,

    /** Cerrada sin sostenerse, también con conclusión escrita. */
    ARCHIVADA;

    /** Si el expediente sigue vivo y los plazos legales corren. */
    public boolean estaAbierta() {
        return this == RECIBIDA || this == EN_INVESTIGACION;
    }
}
