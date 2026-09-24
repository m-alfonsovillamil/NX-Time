package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un mes terminado, visto por quien lo tiene que firmar (Fase B3).
 *
 * @param segundosNetos lo trabajado en el mes, neto de pausas, calculado igual
 *   que lo que se firma: quien firma tiene que ver qué horas está aceptando.
 * @param bloqueo por qué no se puede firmar todavía, en una frase; null si se
 *   puede, o si ya está firmado.
 * @param firma la vigente o, si no la hay, la última invalidada: saber que la
 *   firma de agosto se cayó por una corrección es lo que explica por qué
 *   agosto vuelve a pedir firma.
 */
public record SignableMonthResponse(
        int anio,
        int mes,
        int jornadas,
        long segundosNetos,
        boolean puedeFirmar,
        @Schema(nullable = true) String bloqueo,
        @Schema(nullable = true) MonthlySignatureResponse firma
) {
}
