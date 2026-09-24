package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Firmar el registro de un mes ya terminado (Fase B3). */
public record SignMonthRequest(
        @NotNull @Min(2020) @Max(2100) Integer anio,
        @NotNull @Min(1) @Max(12) Integer mes
) {
}
