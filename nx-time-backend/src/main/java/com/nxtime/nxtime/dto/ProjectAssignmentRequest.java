package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Asignar a alguien a un proyecto, con vigencia.
 *
 * {@code fechaFin} null significa "hasta nuevo aviso", y es el caso
 * normal: casi nadie sabe el día que va a salir de un proyecto cuando
 * entra. Para sacarle se usa el endpoint de finalizar, que pone la fecha
 * de fin — <b>no</b> se borra la asignación: borrarla haría desaparecer
 * sus horas pasadas de ese proyecto, que es justo lo que este diseño
 * quiere evitar.
 */
public record ProjectAssignmentRequest(

        @NotNull(message = "Hay que decir a quién se asigna.")
        Long usuarioId,

        @NotNull(message = "La fecha de inicio de la asignación es obligatoria.")
        LocalDate fechaInicio,

        LocalDate fechaFin
) {
}
