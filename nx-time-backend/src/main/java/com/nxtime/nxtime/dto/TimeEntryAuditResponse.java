package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AuditAction;
import java.time.Instant;

/**
 * Una entrada de la línea temporal de {@code GET
 * /api/v1/auditoria/fichaje/{id}} (Fase 8). valorAnterior/valorNuevo
 * viajan como el JSON crudo que se guardó en su momento (una
 * instantánea del fichaje antes/después del cambio).
 */
public record TimeEntryAuditResponse(
        long id,
        long registroId,
        SimpleUserDTO usuario,
        SimpleUserDTO modificadoPor,
        AuditAction accion,
        String valorAnterior,
        String valorNuevo,
        String motivo,
        Instant fechaHora,
        String ip
) {
}
