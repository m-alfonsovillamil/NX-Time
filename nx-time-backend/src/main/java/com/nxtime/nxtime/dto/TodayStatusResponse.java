package com.nxtime.nxtime.dto;

/**
 * Si hoy es laborable para quien pregunta (ver NonWorkingDayService), y qué
 * hay que preguntar al entrar. Lo piden la web y la app antes de iniciar la
 * jornada; la app lo guarda para cuando no tenga conexión.
 *
 * @param motivo "Festivo: Día de la Hispanidad", "Vacaciones"... null si es laborable.
 * @param preguntarModalidad su empresa pregunta presencial o a distancia (ADR 055)
 */
public record TodayStatusResponse(boolean laborable, String motivo, boolean preguntarModalidad) {

    /** Lo de antes del ADR 055. */
    public TodayStatusResponse(boolean laborable, String motivo) {
        this(laborable, motivo, false);
    }
}
