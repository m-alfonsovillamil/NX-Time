package com.nxtime.nxtime.dto;

import java.util.List;

/**
 * Los proyectos de quien ficha (ADR 017).
 *
 * @param disponibles en los que puede fichar hoy: asignación vigente y
 *     proyecto activo. Con dos o más, la app pregunta al iniciar la jornada;
 *     con uno, lo usa sin preguntar; sin ninguno, se ficha sin proyecto.
 * @param enCurso el proyecto del tramo abierto de la jornada activa, o null
 *     si no hay jornada abierta o se inició sin proyecto.
 */
public record ClockProjectsResponse(List<ProjectOption> disponibles, ProjectOption enCurso) {

    public record ProjectOption(long id, String codigo, String nombre) {
    }
}
