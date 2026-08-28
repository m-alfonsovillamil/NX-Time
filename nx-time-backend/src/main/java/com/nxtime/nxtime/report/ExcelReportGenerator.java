package com.nxtime.nxtime.report;

import java.io.IOException;
import java.io.OutputStream;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Vuelca un {@link MonthlyReport} a una hoja de cálculo (Fase 10).
 *
 * Escribe DIRECTAMENTE en el OutputStream que se le pasa (el de la
 * respuesta HTTP), en vez de construir un byte[] y devolverlo: un
 * informe no tiene por qué caber dos veces en memoria.
 *
 * Las horas van como texto ("07:30"), no como número de Excel: el
 * formato de fecha/hora de Excel es un decimal desde 1900 y, según la
 * configuración regional de quien abra el fichero, se ve de una forma
 * o de otra. Para un documento que puede acabar en una inspección,
 * preferimos que ponga exactamente lo mismo en cualquier ordenador.
 */
@Component
public class ExcelReportGenerator {

    private static final String[] CABECERAS = {
            "Empleado", "Fecha", "Entrada", "Salida", "Pausa (min)", "Tiempo efectivo", "Incidencia"
    };

    public void generar(MonthlyReport informe, OutputStream salida) throws IOException {
        try (Workbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("Horas " + informe.mes());

            CellStyle estiloCabecera = estiloCabecera(libro);
            CellStyle estiloTotal = estiloTotal(libro);

            int numeroFila = 0;
            numeroFila = escribirTitulo(hoja, informe, numeroFila);
            numeroFila = escribirCabeceras(hoja, estiloCabecera, numeroFila);
            numeroFila = escribirFilas(hoja, informe, numeroFila);
            escribirTotales(hoja, informe, estiloTotal, numeroFila);

            for (int i = 0; i < CABECERAS.length; i++) {
                hoja.autoSizeColumn(i);
            }

            libro.write(salida);
        }
    }

    private int escribirTitulo(Sheet hoja, MonthlyReport informe, int numeroFila) {
        Row fila = hoja.createRow(numeroFila++);
        fila.createCell(0).setCellValue("Registro horario - " + informe.nombreEmpresa());

        Row filaMes = hoja.createRow(numeroFila++);
        filaMes.createCell(0).setCellValue("Periodo: " + informe.mes());

        hoja.createRow(numeroFila++); // línea en blanco
        return numeroFila;
    }

    private int escribirCabeceras(Sheet hoja, CellStyle estilo, int numeroFila) {
        Row fila = hoja.createRow(numeroFila++);
        for (int i = 0; i < CABECERAS.length; i++) {
            Cell celda = fila.createCell(i);
            celda.setCellValue(CABECERAS[i]);
            celda.setCellStyle(estilo);
        }
        return numeroFila;
    }

    private int escribirFilas(Sheet hoja, MonthlyReport informe, int numeroFila) {
        for (ReportRow linea : informe.filas()) {
            Row fila = hoja.createRow(numeroFila++);
            fila.createCell(0).setCellValue(linea.nombreEmpleado());
            fila.createCell(1).setCellValue(linea.fecha().toString());
            fila.createCell(2).setCellValue(linea.horaEntrada().toString());
            fila.createCell(3).setCellValue(linea.horaSalida().toString());
            fila.createCell(4).setCellValue(linea.minutosPausa());
            fila.createCell(5).setCellValue(linea.duracionLegible());
            // Se marcan las jornadas que cerró el sistema (Fase 9): son
            // menos fiables que el resto y hay que poder distinguirlas.
            fila.createCell(6).setCellValue(linea.incidencia() ? "Cierre automático" : "");
        }
        return numeroFila;
    }

    private void escribirTotales(Sheet hoja, MonthlyReport informe, CellStyle estilo, int numeroFila) {
        hoja.createRow(numeroFila++); // línea en blanco

        Row fila = hoja.createRow(numeroFila);
        Cell etiqueta = fila.createCell(0);
        etiqueta.setCellValue("TOTAL (" + informe.diasTrabajados() + " días trabajados)");
        etiqueta.setCellStyle(estilo);

        Cell total = fila.createCell(5);
        total.setCellValue(informe.totalLegible());
        total.setCellStyle(estilo);
    }

    private CellStyle estiloCabecera(Workbook libro) {
        Font negrita = libro.createFont();
        negrita.setBold(true);

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(negrita);
        estilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setAlignment(HorizontalAlignment.CENTER);
        return estilo;
    }

    private CellStyle estiloTotal(Workbook libro) {
        Font negrita = libro.createFont();
        negrita.setBold(true);

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(negrita);
        estilo.setBorderTop(BorderStyle.THIN);
        return estilo;
    }
}
