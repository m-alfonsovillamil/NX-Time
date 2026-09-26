package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AnalyticsGrouping;
import java.util.List;

/**
 * El absentismo de un periodo (Fase B4).
 *
 * @param filas una por departamento o por persona, según {@code agrupacion};
 *   vacía si se agrupa por EMPRESA.
 */
public record AbsenteeismResponse(
        AnalyticsWindow ventana,
        AnalyticsGrouping agrupacion,
        AbsenteeismRow total,
        List<AbsenteeismRow> filas
) {
}
