package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AbsenceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /api/v1/ausencias/{id}/estado} (Fase 9), que
 * sustituye a los dos endpoints {@code POST .../aprobar/{id}} y
 * {@code POST .../rechazar/{id}}: cambiar el estado de un recurso que ya
 * existe es un PATCH, no dos POST distintos con la acción en la URL.
 *
 * El comentario es opcional en general, pero {@link
 * com.nxtime.nxtime.service.impl.AbsenceServiceImpl} lo exige al
 * RECHAZAR: negar una ausencia sin explicar por qué no es aceptable de
 * cara al empleado.
 */
public record UpdateAbsenceStatusRequest(

        @NotNull(message = "El nuevo estado es obligatorio.")
        AbsenceStatus estado,

        @Size(max = 500, message = "El comentario no puede superar los 500 caracteres.")
        String comentario
) {
}
