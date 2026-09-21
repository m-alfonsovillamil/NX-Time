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
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
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

    /**
     * Anchos de columna, en caracteres.
     *
     * A mano y no con {@code autoSizeColumn} porque SXSSF no puede medir filas
     * que ya ha mandado al disco: para autoajustar habría que retener todas en
     * memoria, que es justo lo que se está evitando. Están calculados sobre el
     * contenido real de cada columna -- un nombre completo, una fecha ISO, una
     * hora "HH:mm" -- y son de sobra.
     */
    private static final int[] ANCHOS = {28, 12, 10, 10, 12, 16, 20};

    /**
     * Filas que se quedan en memoria antes de ir volcándose al disco.
     *
     * Cien es la ventana habitual de SXSSF: suficiente para que escribir sea
     * secuencial y barato, y poco para que el libro no crezca con el número de
     * jornadas.
     */
    private static final int VENTANA_DE_FILAS = 100;

    public void generar(MonthlyReport informe, OutputStream salida) throws IOException {
        // SXSSF y no XSSF: XSSFWorkbook construye el libro ENTERO en memoria
        // antes de escribir el primer byte, lo que convertía en una
        // contradicción el StreamingResponseBody del controlador -- el
        // streaming era solo de cara al socket, mientras que dentro el informe
        // se materializaba dos veces (las entidades y luego el libro). Con
        // SXSSF solo conviven VENTANA_DE_FILAS filas.
        // El try-with-resources no es opcional aquí: SXSSF respalda las filas
        // que saca de memoria en ficheros temporales del disco, y close() es
        // lo que los borra (así lo documenta POI 5.5: "disposes of the
        // temporary files backing this workbook on disk"). Sin cerrarlo se
        // acumularían hasta llenar el disco efímero del contenedor. El
        // dispose() que hacía falta en versiones antiguas está deprecado.
        try (SXSSFWorkbook libro = new SXSSFWorkbook(VENTANA_DE_FILAS)) {
            // Los temporales van comprimidos: el disco de Render es efímero y
            // pequeño, y un informe anual de una empresa grande no es poca
            // cosa. No afecta al fichero que se entrega, solo a lo que SXSSF
            // deja en el disco mientras lo construye.
            libro.setCompressTempFiles(true);

            Sheet hoja = libro.createSheet("Horas " + informe.mes());
            for (int i = 0; i < ANCHOS.length; i++) {
                hoja.setColumnWidth(i, ANCHOS[i] * 256);
            }

            CellStyle estiloCabecera = estiloCabecera(libro);
            CellStyle estiloTotal = estiloTotal(libro);

            int numeroFila = 0;
            numeroFila = escribirTitulo(hoja, informe, numeroFila);
            numeroFila = escribirCabeceras(hoja, estiloCabecera, numeroFila);
            numeroFila = escribirFilas(hoja, informe, numeroFila);
            escribirTotales(hoja, informe, estiloTotal, numeroFila);

            libro.write(salida);
            // Vaciar lo que quede en el búfer antes de dar por escrito el
            // fichero. El generador de PDF cierra su documento y con él el
            // flujo, así que sus descargas terminaban bien; esta no, y si algo
            // aborta la petición después, lo último del libro no llega nunca.
            salida.flush();
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
