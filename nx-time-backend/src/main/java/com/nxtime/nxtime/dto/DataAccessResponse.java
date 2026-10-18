package com.nxtime.nxtime.dto;

import java.time.Instant;

/**
 * Una consulta de datos personales ajenos, para el ADMIN (V44, ADR 047).
 *
 * @param quien nombre de quien consultó, tal como es hoy: si se anonimizó,
 *     sale anonimizado
 * @param sobre de quién eran los datos; null si eran de muchos o si no se
 *     anota (una denuncia)
 */
public record DataAccessResponse(
        Instant en,
        long quienId,
        String quien,
        String que,
        Long sobreId,
        String sobre,
        String detalle) {
}
