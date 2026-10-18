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
@Schema(description = "Resultado de comprobar la cadena de hashes de la auditoría. La cadena es común a todas "
        + "las empresas de la instalación y se recorre entera, pero las cifras son solo las de la empresa de quien pregunta")
public record AuditIntegrityResponse(

        @Schema(description = "Si la traza está intacta hasta donde se puede comprobar")
        boolean intacta,

        @Schema(description = "Movimientos de auditoría de mi empresa revisados")
        long movimientos,

        @Schema(description = "De esos, a cuántos se les ha recalculado el hash y cuadra")
        long comprobados,

        @Schema(description = "Movimientos antiguos de los que solo se ha podido comprobar el enlace "
                + "con el anterior: su hash no se puede recalcular (ver V26)")
        long soloEnlace,

        @Schema(description = "Id del primer movimiento con problemas, si lo hay y es de mi empresa")
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

    /**
     * El tramo común de la traza (lo de antes de V46, ADR 050) falla en un
     * movimiento que NO es de la empresa que pregunta. Se le dice que falla,
     * porque lo suyo de ese tramo posterior a ese punto se ha quedado sin
     * comprobar; no se le dice en qué movimiento, que es de otra empresa. Quien
     * mantiene el servicio lo tiene en el log.
     */
    public static AuditIntegrityResponse rotaEnOtraParte(long movimientos, long comprobados, long soloEnlace) {
        return new AuditIntegrityResponse(false, movimientos, comprobados, soloEnlace, null,
                "El problema está en un movimiento que no es de tu empresa, en el tramo de la traza anterior a "
                        + "octubre de 2026, que es común a todas las empresas de este servicio: tus movimientos de ese "
                        + "tramo posteriores a ese punto no se han podido comprobar. Tu cadena propia, la de después, "
                        + "se comprueba aparte. Avisa a quien administra el servicio.");
    }
}
