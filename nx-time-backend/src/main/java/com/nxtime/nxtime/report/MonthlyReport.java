package com.nxtime.nxtime.report;

import java.time.YearMonth;
import java.util.List;

/**
 * Datos ya preparados de un informe mensual (Fase 10), listos para que
 * un generador los vuelque a Excel o a PDF.
 *
 * Existe para que los dos generadores partan EXACTAMENTE de lo mismo:
 * si cada uno consultara y calculara por su cuenta, el Excel y el PDF
 * del mismo mes podrían no cuadrar, que es el peor defecto posible en
 * un documento que se entrega ante una inspección.
 */
public record MonthlyReport(
        String nombreEmpresa,
        String nombreEmpleado,
        YearMonth mes,
        List<ReportRow> filas
) {

    public long totalMinutos() {
        return filas.stream().mapToLong(ReportRow::minutosNetos).sum();
    }

    public String totalLegible() {
        long total = totalMinutos();
        return (total / 60) + "h " + String.format("%02dm", total % 60);
    }

    public long diasTrabajados() {
        return filas.stream().map(ReportRow::fecha).distinct().count();
    }

    /**
     * Jornadas que cerró el sistema por no tener fichaje de salida
     * (Fase 9). Se señalan en el informe en vez de esconderlas: son
     * datos menos fiables que el resto y quien lo lea debe saberlo.
     */
    public long incidencias() {
        return filas.stream().filter(ReportRow::incidencia).count();
    }
}
