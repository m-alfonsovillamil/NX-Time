package com.nxtime.nxtime.domain;

/**
 * Por qué saltó el aviso de horas extra (Fase F).
 *
 * Son dos umbrales distintos porque miden cosas distintas, y por eso un
 * mismo día puede disparar los dos: pasarse una tarde no es lo mismo que
 * acumular exceso toda la semana.
 */
public enum OvertimeType {

    /**
     * Más de 9 h de trabajo efectivo en una jornada (art. 34.3 ET).
     *
     * El límite es legal y fijo: no depende de la jornada contratada.
     * Alguien con media jornada que trabaja diez horas un día se pasa
     * igual.
     */
    DIARIA,

    /**
     * Más de la jornada semanal contratada, <b>prorrateada por los días
     * hábiles reales de esa semana</b>.
     *
     * Lo de "reales" es la parte que importa: dividir las horas
     * semanales entre cinco a ciegas daría un falso positivo en cada
     * puente, porque una semana con festivo tiene menos días para
     * repartir las mismas horas.
     */
    SEMANAL
}
