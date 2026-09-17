package com.nxtime.nxtime.dto;

import java.time.Instant;

/**
 * Una pausa añadida a posteriori, tal como se enseña.
 *
 * @param porAprobacion si entró al aprobarse una corrección (día pasado) o
 *     se aplicó directamente (la jornada de hoy)
 */
public record AddedPauseDTO(
        long id,
        Instant inicio,
        Instant fin,
        long minutos,
        String motivo,
        SimpleUserDTO creadaPor,
        Instant creadaEn,
        boolean porAprobacion
) {
}
