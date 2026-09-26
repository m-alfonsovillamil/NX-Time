package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * La puntualidad de un grupo.
 *
 * Solo mide a quien tiene cuadrante: sin horario teórico no hay hora a la que
 * llegar tarde. {@code entradasConHorario} son los días que la persona tenía
 * horario y fichó; de esos, {@code retrasos} son los que tienen una incidencia
 * de retraso (más de 10 minutos, Fase B2), y el resto son {@code puntuales}.
 *
 * @param puntualidad puntuales sobre entradas con horario, en porcentaje. Null
 *   si no hubo ninguna.
 * @param retrasoMedioMinutos la media de los retrasos, no de todas las
 *   entradas: "cuánto tarde, cuando se llega tarde". Null si no hubo ninguno.
 * @param retrasoMedianoMinutos la mediana, que no se deja arrastrar por un
 *   único retraso de tres horas.
 * @param retrasosHasta30 de 11 a 30 minutos.
 * @param retrasosDeMasDe30 más de 30 minutos.
 */
public record PunctualityRow(
        @Schema(nullable = true) Long id,
        String nombre,
        int entradasConHorario,
        int puntuales,
        int retrasos,
        @Schema(nullable = true, example = "93.5") BigDecimal puntualidad,
        @Schema(nullable = true) Double retrasoMedioMinutos,
        @Schema(nullable = true) Double retrasoMedianoMinutos,
        int retrasosHasta30,
        int retrasosDeMasDe30
) {
}
