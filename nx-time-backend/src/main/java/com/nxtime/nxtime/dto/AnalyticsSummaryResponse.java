package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Las cifras de un vistazo (Fase B4): la tarjeta del panel de la app y la
 * cabecera de la web. Los porcentajes son los mismos que el total de
 * {@code /absentismo} y {@code /puntualidad}, calculados igual.
 *
 * @param personas cuántas personas entran en la cuenta.
 * @param jornadasIncompletas qué parte de las jornadas cerradas las cerró el
 *   sistema por falta de fichaje de salida, en porcentaje. Un número alto
 *   dice que las demás cifras descansan sobre datos flojos.
 * @param minutosMediosPorDia lo trabajado de media en un día con jornada,
 *   descontando pausas.
 */
public record AnalyticsSummaryResponse(
        AnalyticsWindow ventana,
        int personas,
        @Schema(nullable = true) BigDecimal absentismo,
        @Schema(nullable = true) BigDecimal absentismoSinJustificar,
        @Schema(nullable = true) BigDecimal puntualidad,
        @Schema(nullable = true) Double retrasoMedioMinutos,
        @Schema(nullable = true) BigDecimal jornadasIncompletas,
        @Schema(nullable = true) Long minutosMediosPorDia
) {
}
