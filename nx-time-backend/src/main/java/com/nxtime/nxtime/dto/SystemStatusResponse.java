package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.ScheduledTaskResult;
import java.time.Instant;
import java.util.List;

/**
 * Si las tareas programadas han corrido, para quien vigila desde fuera
 * (el workflow {@code tareas-nocturnas.yml}).
 *
 * El endpoint es público, así que aquí no viaja el {@code detalle} de
 * cada ejecución: "Cerradas 3 jornadas sin fichaje de salida" ya es
 * información de una empresa. Solo cuándo corrió y cómo acabó.
 *
 * @param ok true solo si todas las tareas lo están
 */
public record SystemStatusResponse(boolean ok, List<TaskStatus> tareas) {

    /**
     * @param debioCorrer     la última hora programada que ya debería haber terminado
     * @param ok              si alguna ejecución terminó bien desde {@code debioCorrer}
     * @param ultimaEjecucion cuándo empezó la última, acabara como acabara
     * @param ultimoResultado cómo acabó esa última
     * @param ultimaCorrecta  cuándo empezó la última que terminó bien
     */
    public record TaskStatus(
            ScheduledTask tarea,
            Instant debioCorrer,
            boolean ok,
            Instant ultimaEjecucion,
            ScheduledTaskResult ultimoResultado,
            Instant ultimaCorrecta) {
    }
}
