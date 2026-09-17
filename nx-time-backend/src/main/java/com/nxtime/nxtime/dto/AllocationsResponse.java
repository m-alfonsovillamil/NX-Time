package com.nxtime.nxtime.dto;

import java.util.List;

/**
 * El reparto por proyecto de una jornada (ADR 017).
 *
 * @param netoMinutos lo trabajado sin pausas: es lo que tiene que sumar el
 *     reparto para aplicarse directamente.
 * @param repartoLibre si la jornada es de la semana en curso y su dueño puede
 *     repartirla sin permiso. Fuera de plazo, el reparto se pide y lo aprueba
 *     un gestor.
 * @param lineas lo imputado ahora mismo, por proyecto.
 * @param disponibles los proyectos entre los que se puede repartir: los que
 *     esa persona tenía asignados ese día.
 * @param solicitudPendienteId la solicitud viva sobre este fichaje, si la hay:
 *     mientras exista, no se puede repartir otra vez.
 */
public record AllocationsResponse(
        long fichajeId,
        long netoMinutos,
        boolean repartoLibre,
        List<Linea> lineas,
        List<ClockProjectsResponse.ProjectOption> disponibles,
        Long solicitudPendienteId
) {
    public record Linea(long proyectoId, String codigo, String nombre, long minutos) {
    }
}
