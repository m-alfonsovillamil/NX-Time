package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AnalyticsGrouping;
import java.util.List;

/** La puntualidad de un periodo (Fase B4). Ver {@link PunctualityRow}. */
public record PunctualityResponse(
        AnalyticsWindow ventana,
        AnalyticsGrouping agrupacion,
        PunctualityRow total,
        List<PunctualityRow> filas
) {
}
