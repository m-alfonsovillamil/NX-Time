package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * El resultado de comprobar la traza de auditoría.
 *
 * Está pensado para poder enseñarse: es lo que contesta a "demuéstrame que
 * este registro no se ha tocado". Por eso lleva las cifras y no solo un sí o
 * un no, y por eso separa las filas comprobadas de las que solo admiten la
 * comprobación del enlace.
 */
@Schema(description = "Resultado de comprobar la cadena de hashes de la auditoría")
public record AuditIntegrityResponse(

        @Schema(description = "Si la traza está intacta hasta donde se puede comprobar")
        boolean intacta,

        @Schema(description = "Movimientos de auditoría revisados")
        long movimientos,

        @Schema(description = "Movimientos cuyo hash se ha recalculado y cuadra")
        long comprobados,

        @Schema(description = "Movimientos antiguos de los que solo se ha podido comprobar el enlace "
                + "con el anterior: su hash no se puede recalcular (ver V26)")
        long soloEnlace,

        @Schema(description = "Id del primer movimiento con problemas, si lo hay")
        Long primerFallo,

        @Schema(description = "Qué le pasa a ese movimiento, en una frase")
        String motivo
) {

    public static AuditIntegrityResponse intacta(long movimientos, long comprobados, long soloEnlace) {
        return new AuditIntegrityResponse(true, movimientos, comprobados, soloEnlace, null, null);
    }

    public static AuditIntegrityResponse rota(
            long movimientos, long comprobados, long soloEnlace, long primerFallo, String motivo) {
        return new AuditIntegrityResponse(false, movimientos, comprobados, soloEnlace, primerFallo, motivo);
    }
}
