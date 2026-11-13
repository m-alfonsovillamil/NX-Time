package com.nxtime.nxtime.report;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Una línea de informe: una jornada ya cerrada, con las horas en la
 * zona ESPAÑOLA (Fase 10).
 *
 * Los instantes se proyectan a hora local aquí, en el modelo del
 * informe, y no en cada generador: si lo hiciera cada uno por su
 * cuenta, el Excel y el PDF podrían acabar mostrando horas distintas
 * para el mismo fichaje.
 *
 * El tiempo trabajado se guarda en **SEGUNDOS**, no en minutos, y los
 * minutos son un valor derivado que solo se usa al presentar.
 *
 * No es un capricho: antes la fila guardaba los minutos ya truncados y
 * {@link MonthlyReport} sumaba esos valores, con lo que cada jornada
 * perdía hasta 59 segundos y el total de un mes de 22 días podía
 * quedarse hasta 21 minutos corto -- en el PDF que se firma y se
 * entrega ante una inspección. Es el mismo error que la auditoría
 * inicial del proyecto encontró en el cómputo de pausas y que se
 * resolvió igual: **agregar en la unidad exacta y redondear solo al
 * mostrar**. Ver MonthlyReportTotalTest.
 *
 * "segundosNetos" ya lleva descontadas las pausas, que es lo que cuenta
 * como tiempo de trabajo efectivo.
 *
 * Las pausas, igual, en segundos ({@code segundosPausa}). Hasta noviembre de
 * 2026 la fila llevaba los minutos ya truncados, y quien sumaba la columna
 * «Pausa (min)» del Excel perdía hasta 59 segundos por jornada.
 */
public record ReportRow(
        String nombreEmpleado,
        LocalDate fecha,
        LocalTime horaEntrada,
        LocalTime horaSalida,
        long segundosPausa,
        long segundosNetos,
        boolean incidencia,
        /** Su número en el ERP o en nóminas (ADR 053), o null. */
        String codigoEmpleado,
        /** Presencial o a distancia (ADR 055), o null si no consta. */
        com.nxtime.nxtime.domain.Modalidad modalidad
) {

    /** Sin código de empleado. */
    public ReportRow(String nombreEmpleado, LocalDate fecha, LocalTime horaEntrada, LocalTime horaSalida,
            long segundosPausa, long segundosNetos, boolean incidencia) {
        this(nombreEmpleado, fecha, horaEntrada, horaSalida, segundosPausa, segundosNetos, incidencia, null, null);
    }

    /** Sin modalidad (lo de antes del ADR 055). */
    public ReportRow(String nombreEmpleado, LocalDate fecha, LocalTime horaEntrada, LocalTime horaSalida,
            long segundosPausa, long segundosNetos, boolean incidencia, String codigoEmpleado) {
        this(nombreEmpleado, fecha, horaEntrada, horaSalida, segundosPausa, segundosNetos, incidencia, codigoEmpleado,
                null);
    }

    /**
     * Minutos trabajados de ESTA jornada, truncados. Vale para mostrar
     * una fila; NO para sumar varias -- para eso está
     * {@link MonthlyReport#totalSegundos()}.
     */
    public long minutosNetos() {
        return segundosNetos / 60;
    }

    /**
     * Minutos de pausa de ESTA jornada, redondeados al más cercano: lo que
     * enseña el PDF, y lo mismo que enseña el Excel con su formato «0». Solo
     * para mostrar; el Excel guarda el valor exacto para que la suma cuadre.
     */
    public long minutosPausa() {
        return Math.round(segundosPausa / 60.0);
    }

    /** Formato "7h 30m", el que entiende cualquiera que abra el informe. */
    public String duracionLegible() {
        long minutos = minutosNetos();
        return (minutos / 60) + "h " + String.format("%02dm", minutos % 60);
    }
}
