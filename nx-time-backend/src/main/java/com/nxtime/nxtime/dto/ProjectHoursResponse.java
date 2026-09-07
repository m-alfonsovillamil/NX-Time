package com.nxtime.nxtime.dto;

import java.util.List;

/**
 * Horas imputadas a los proyectos de un mes.
 *
 * Los minutos van en minutos y no en horas decimales: "7h 30m" es lo que
 * se lee en pantalla, y un 7,5 obligaría al cliente a deshacer una
 * división que además pierde precisión. Es el mismo criterio que el
 * resto de agregados del panel.
 *
 * Solo aparecen los proyectos <b>con horas en ese mes</b>: un listado
 * lleno de ceros no dice nada, y para ver todos los proyectos ya está su
 * propia pantalla.
 */
public record ProjectHoursResponse(
        int anio,
        int mes,
        List<ProjectHoursItem> proyectos
) {

    /**
     * Un proyecto y sus minutos.
     *
     * @param codigo se manda además del nombre porque es lo que
     *   identifica al proyecto en un informe, y un nombre largo se
     *   corta en una barra estrecha.
     */
    public record ProjectHoursItem(
            long proyectoId,
            String codigo,
            String nombre,
            long minutos
    ) {
    }
}
