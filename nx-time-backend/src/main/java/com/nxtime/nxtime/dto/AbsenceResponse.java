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
        // Con decimales desde el ADR 063: un medio día son 0,5.
        double diasHabiles,
        boolean medioDia,
        // ADR 058: quién la canceló, cuándo y por qué (nulos si no está
        // CANCELADA), y si quien pregunta puede cancelarla ahora. La regla
        // viaja resuelta, como «puedoResolver» en las correcciones: los
        // clientes no la reimplementan.
        SimpleUserDTO canceladaPor,
        Instant canceladaEn,
        String motivoCancelacion,
        boolean puedoCancelar
) {
}
