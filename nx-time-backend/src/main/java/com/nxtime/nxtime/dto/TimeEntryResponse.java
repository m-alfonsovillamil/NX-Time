package com.nxtime.nxtime.dto;

import java.time.LocalDateTime;

/**
 * Respuesta de los endpoints de fichaje. Sustituye a exponer la entidad
 * TimeEntry directamente (ver auditoría, defecto #1): antes, al viajar
 * la entidad completa, se arrastraba el Usuario anidado -- y con él, su
 * contraseña cifrada.
 */
public record TimeEntryResponse(
        long id,
        LocalDateTime horaEntrada,
        LocalDateTime horaSalida,
        boolean enPausa,
        long minutosPausaAcumulados
) {
}
