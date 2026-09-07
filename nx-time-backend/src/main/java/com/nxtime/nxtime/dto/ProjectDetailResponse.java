package com.nxtime.nxtime.dto;

import java.util.List;

/**
 * Un proyecto abierto en detalle: sus datos, quién ha pasado por él y
 * cuántas horas ha puesto cada uno en el mes consultado.
 *
 * Va en una sola respuesta y no en tres endpoints porque la pantalla no
 * puede pintar nada hasta tener las tres cosas (mismo criterio que
 * {@link CalendarResponse}).
 *
 * Ojo con la relación entre las dos listas: <b>no son la misma gente</b>.
 * En {@code asignaciones} está todo el histórico, incluida quien estuvo
 * el año pasado; en {@code horas} solo quien trabajó en el mes pedido.
 * Alguien asignado que no fichó ese mes no aparece en {@code horas}, y
 * eso es correcto: no puso horas.
 */
public record ProjectDetailResponse(
        ProjectResponse proyecto,
        List<ProjectAssignmentResponse> asignaciones,
        int anio,
        int mes,
        List<EmployeeHoursItem> horas
) {

    public record EmployeeHoursItem(long usuarioId, String nombre, long minutos) {
    }
}
