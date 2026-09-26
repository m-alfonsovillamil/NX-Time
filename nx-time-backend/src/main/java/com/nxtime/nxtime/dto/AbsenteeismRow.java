package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * El absentismo de un grupo: la empresa, un departamento o una persona.
 *
 * Todo en días. {@code diasLaborables} es el denominador: los días que se
 * debían trabajar, sin las vacaciones. Se cumple siempre que
 * {@code diasLaborables = diasTrabajados + diasAusenciaJustificada + diasSinFichaje}.
 *
 * @param id el de la persona o el del departamento; null en el total y en
 *   "Sin departamento".
 * @param absentismo días perdidos (con o sin motivo) sobre días laborables, en
 *   porcentaje con un decimal. Null si no hubo ningún día laborable: un 0 %
 *   afirmaría que nadie faltó.
 * @param absentismoSinJustificar solo los días sin fichaje ni ausencia.
 * @param motivos el desglose de {@code diasAusenciaJustificada}, de más a menos días.
 */
public record AbsenteeismRow(
        @Schema(nullable = true) Long id,
        String nombre,
        int personas,
        int diasLaborables,
        int diasTrabajados,
        int diasAusenciaJustificada,
        int diasSinFichaje,
        int diasVacaciones,
        @Schema(nullable = true, example = "4.2") BigDecimal absentismo,
        @Schema(nullable = true, example = "0.8") BigDecimal absentismoSinJustificar,
        List<AbsenceReasonDays> motivos
) {
}
