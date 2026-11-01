package com.nxtime.nxtime.domain;

/**
 * Dónde se trabaja en una jornada (V51, ADR 055): en el centro de trabajo o a
 * distancia. Es el valor de {@code registros.modalidad} y de su CHECK.
 */
public enum Modalidad {
    PRESENCIAL,
    A_DISTANCIA;

    /** Para los informes: «Presencial», «A distancia», o vacío si no consta. */
    public static String texto(Modalidad modalidad) {
        if (modalidad == null) {
            return "";
        }
        return modalidad == PRESENCIAL ? "Presencial" : "A distancia";
    }
}
