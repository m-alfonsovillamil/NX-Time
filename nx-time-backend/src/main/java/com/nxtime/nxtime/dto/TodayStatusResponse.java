package com.nxtime.nxtime.dto;

/**
 * Si hoy es laborable para quien pregunta (ver NonWorkingDayService).
 *
 * @param motivo "Festivo: Día de la Hispanidad", "Vacaciones"... null si es laborable.
 */
public record TodayStatusResponse(boolean laborable, String motivo) {
}
