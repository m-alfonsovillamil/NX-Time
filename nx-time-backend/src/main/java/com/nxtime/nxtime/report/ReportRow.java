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
 * "minutosNetos" ya lleva descontadas las pausas, que es lo que cuenta
 * como tiempo de trabajo efectivo.
 */
public record ReportRow(
        String nombreEmpleado,
        LocalDate fecha,
        LocalTime horaEntrada,
        LocalTime horaSalida,
        long minutosPausa,
        long minutosNetos,
        boolean incidencia
) {

    /** Formato "7h 30m", el que entiende cualquiera que abra el informe. */
    public String duracionLegible() {
        return (minutosNetos / 60) + "h " + String.format("%02dm", minutosNetos % 60);
    }
}
