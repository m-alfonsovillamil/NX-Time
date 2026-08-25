package com.nxtime.nxtime.dto;

import java.time.Instant;

/**
 * Respuesta de los endpoints de fichaje. Sustituye a exponer la entidad
 * TimeEntry directamente (ver auditoría, defecto #1): antes, al viajar
 * la entidad completa, se arrastraba el Usuario anidado -- y con él, su
 * contraseña cifrada.
 *
 * horaEntrada/horaSalida son Instant desde la Fase 3 (antes
 * LocalDateTime): viajan en JSON como ISO-8601 con sufijo "Z" (UTC),
 * sin ambigüedad de zona horaria. La app cliente los presenta en la
 * zona que corresponda.
 */
public record TimeEntryResponse(
        long id,
        Instant horaEntrada,
        Instant horaSalida,
        boolean enPausa,
        long minutosPausaAcumulados
) {
}
