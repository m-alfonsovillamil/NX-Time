package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Una plantilla de horario, con sus tramos y lo que ya se puede o no hacer con
 * ella.
 *
 * @param minutosSemanales la suma de sus tramos: lo que se compara con la
 *   jornada contratada al asignarla.
 * @param tramosEditables false si ya se ha aplicado a algún día pasado:
 *   cambiarle los tramos reescribiría el horario teórico de esos días.
 * @param borrable false si alguien la tiene o la ha tenido asignada.
 */
public record ScheduleTemplateResponse(
        long id,
        String nombre,
        String descripcion,
        long minutosSemanales,
        boolean tramosEditables,
        boolean borrable,
        List<Tramo> tramos
) {

    public record Tramo(
            int diaSemana,
            int inicio,
            int fin,
            @Schema(example = "09:00") String horaInicio,
            @Schema(example = "14:00") String horaFin,
            int minutos,
            @Schema(description = "Si termina al día siguiente: el turno de noche.")
            boolean cruzaMedianoche
    ) {
    }
}
