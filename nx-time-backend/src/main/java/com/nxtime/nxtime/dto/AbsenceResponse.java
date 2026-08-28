package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO para enviar la información de una ausencia a la app.
 *
 * Desde la Fase 9 incluye la trazabilidad de la resolución
 * (aprobadoPor/fechaResolucion/comentarioResolucion, los tres nulos
 * mientras la petición siga PENDIENTE) y "diasHabiles", los días que
 * realmente consume la ausencia descontando fines de semana y festivos
 * -- antes el cliente solo podía restar fechas, contando sábados.
 */
public record AbsenceResponse(
        long id,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        AbsenceType tipo,
        String motivo,
        AbsenceStatus estado,
        SimpleUserDTO usuario,
        SimpleUserDTO aprobadoPor,
        Instant fechaResolucion,
        String comentarioResolucion,
        int diasHabiles
) {
}
