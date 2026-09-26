package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AnalyticsPeriod;
import com.nxtime.nxtime.domain.AnalyticsScope;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Sobre qué se ha calculado una respuesta de la analítica (Fase B4).
 *
 * @param desde el primer día del periodo.
 * @param hasta el último día del periodo, aunque todavía no haya llegado.
 * @param evaluadoHasta el último día que de verdad se ha contado: ayer, si el
 *   periodo está en curso, porque hoy aún no ha terminado y contarlo pondría a
 *   media plantilla como ausente a las diez de la mañana. Null si el periodo
 *   no tiene todavía ningún día terminado (el día 1 del mes).
 * @param departamento el nombre del departamento si el alcance es
 *   DEPARTAMENTO; null si es la empresa.
 */
public record AnalyticsWindow(
        AnalyticsPeriod periodo,
        LocalDate desde,
        LocalDate hasta,
        @Schema(nullable = true) LocalDate evaluadoHasta,
        AnalyticsScope alcance,
        @Schema(nullable = true) String departamento
) {
}
