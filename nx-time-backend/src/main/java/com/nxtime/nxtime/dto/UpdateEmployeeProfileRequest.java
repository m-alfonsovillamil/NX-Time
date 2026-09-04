package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * Cuerpo de {@code PATCH /api/v1/gestor/empleados/{id}/ficha}.
 *
 * <b>Los dos campos son opcionales y null significa "no tocar"</b>, que
 * es lo que significa un PATCH: un formulario que solo edita la jornada
 * no tiene por qué reenviar un dato de vacaciones que no gestiona. Un
 * cuerpo vacío es un 200 sin efecto. La contrapartida asumida es que no
 * se puede "borrar" ninguno de los dos valores -- da igual, ambos
 * tienen un valor por defecto sensato (40 h y 22 días) y ninguno tiene
 * un null con significado propio.
 *
 * Los rangos son un espejo exacto de los CHECK que V4 puso en la base
 * ({@code horas_semanales > 0 AND <= 60}, {@code dias_totales >= 0}).
 * Validarlos aquí convierte lo que si no sería un 500 por violación de
 * constraint en un 400 con su ProblemDetail.
 *
 * @param horasSemanales jornada contractual. BigDecimal y no int porque
 *                       37,5 h es una jornada real y frecuente; la
 *                       columna es NUMERIC(4,1).
 * @param diasVacaciones derecho anual del AÑO EN CURSO (Europe/Madrid).
 *                       El saldo se guarda por año, así que "los días de
 *                       vacaciones" a secas no existe.
 */
public record UpdateEmployeeProfileRequest(

        @DecimalMin(value = "0.1", message = "La jornada semanal debe ser mayor que cero.")
        @DecimalMax(value = "60.0", message = "La jornada semanal no puede superar las 60 horas.")
        @Digits(integer = 2, fraction = 1, message = "La jornada semanal admite como mucho un decimal.")
        BigDecimal horasSemanales,

        @Min(value = 0, message = "Los días de vacaciones no pueden ser negativos.")
        @Max(value = 365, message = "Los días de vacaciones no pueden superar los 365.")
        Integer diasVacaciones
) {
}
