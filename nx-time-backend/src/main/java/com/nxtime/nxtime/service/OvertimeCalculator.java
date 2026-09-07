package com.nxtime.nxtime.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Los dos umbrales de horas extra, sin base de datos de por medio
 * (Fase F).
 *
 * Es una clase sin estado a propósito: el cálculo se puede probar con
 * números concretos, que es justo lo que hace falta cuando la pregunta
 * es "¿esta semana con un festivo se pasa o no?".
 *
 * <b>Tolerancia de 30 minutos.</b> Pasarse cinco minutos no es una hora
 * extra: es la diferencia entre fichar al llegar a la mesa o al entrar
 * por la puerta. Sin tolerancia, el proceso nocturno generaría un aviso
 * casi todos los días para casi todo el mundo, y una bandeja llena de
 * avisos que nadie mira es peor que no tener avisos.
 */
public final class OvertimeCalculator {

    private OvertimeCalculator() {
    }

    /** Art. 34.3 ET: la jornada ordinaria no pasa de 9 h efectivas. */
    public static final int MINUTOS_MAXIMOS_POR_JORNADA = 9 * 60;

    /** Art. 35.2 ET: tope anual de horas extra. */
    public static final int MINUTOS_BOLSA_ANUAL = 80 * 60;

    /** Por debajo de esto no se avisa. Ver el Javadoc de la clase. */
    public static final int TOLERANCIA_MINUTOS = 30;

    /**
     * Minutos que sobran de una jornada, o 0 si no llega a la tolerancia.
     *
     * El límite diario es LEGAL y fijo: no depende de la jornada
     * contratada. Alguien a media jornada que trabaja diez horas un día
     * se pasa igual que quien tiene jornada completa.
     */
    public static int excesoDiario(long minutosTrabajados) {
        return exceso(minutosTrabajados, MINUTOS_MAXIMOS_POR_JORNADA);
    }

    /**
     * Minutos que sobran de una semana, o 0 si no llega a la tolerancia.
     *
     * <b>El objetivo se prorratea por los días hábiles REALES de esa
     * semana</b>, no se divide entre cinco. Es la diferencia entre
     * detectar horas extra y castigar los puentes: en una semana con un
     * festivo, quien trabaja sus horas normales de lunes a jueves no ha
     * hecho ni un minuto de más, pero contra un objetivo de 40 h saldría
     * "por debajo" y contra uno de 32 h calculado a ojo podría salir
     * "por encima" cualquier día que se alargue un poco.
     *
     * @param horasSemanales jornada contratada (37,5 es normal).
     * @param diasHabiles los que quedan tras descontar festivos y
     *   ausencias aprobadas (ver {@link WorkingDayService}).
     * @return 0 si la semana no tiene días hábiles — una semana entera de
     *   vacaciones no puede generar horas extra, y dividir entre cero
     *   sería el fallo más tonto posible aquí.
     */
    public static int excesoSemanal(long minutosTrabajados, BigDecimal horasSemanales, int diasHabiles) {
        if (diasHabiles <= 0) {
            return 0;
        }
        return exceso(minutosTrabajados, objetivoSemanal(horasSemanales, diasHabiles));
    }

    /**
     * Los minutos que se esperan de una semana con {@code diasHabiles}
     * días.
     *
     * Se prorratea sobre una semana de cinco días laborables, que es lo
     * que representa la jornada contratada: 37,5 h en una semana normal
     * son 30 h en una de cuatro días.
     */
    public static long objetivoSemanal(BigDecimal horasSemanales, int diasHabiles) {
        if (horasSemanales == null || diasHabiles <= 0) {
            return 0;
        }
        // Con BigDecimal y no con double: 37,5 / 5 * 4 en coma flotante
        // da 29,999999999999996, y truncar eso son 1799 minutos en vez de
        // 1800 -- un minuto de exceso inventado cada semana.
        return horasSemanales
                .multiply(BigDecimal.valueOf(60))
                .multiply(BigDecimal.valueOf(diasHabiles))
                .divide(BigDecimal.valueOf(5), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    /** Cuánto queda de la bolsa anual, nunca negativo. */
    public static int minutosRestantesDeLaBolsa(int minutosConsumidos) {
        return Math.max(0, MINUTOS_BOLSA_ANUAL - minutosConsumidos);
    }

    /**
     * Si conviene avisar de que la bolsa se está agotando.
     *
     * Al 80 % y no al 100 %: enterarse de que te has pasado del tope
     * legal cuando ya te has pasado no le sirve a nadie. El aviso existe
     * para poder no llegar.
     */
    public static boolean bolsaCercaDelLimite(int minutosConsumidos) {
        return minutosConsumidos >= MINUTOS_BOLSA_ANUAL * 80 / 100;
    }

    private static int exceso(long minutosTrabajados, long objetivo) {
        long sobra = minutosTrabajados - objetivo;
        return sobra > TOLERANCIA_MINUTOS ? (int) sobra : 0;
    }
}
