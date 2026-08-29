package com.nxtime.nxtime.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Genera los informes de verdad y vuelve a abrirlos (Fase 10).
 *
 * Un test que solo comprobara "no lanza excepción" no valdría de nada:
 * un Excel o un PDF corrupto se escriben sin protestar y el fallo solo
 * aparece cuando alguien intenta abrir el fichero. Por eso el Excel se
 * relee con POI y se comprueban las celdas, y del PDF se verifica al
 * menos que empieza por su número mágico y termina bien.
 */
class ReportGeneratorTest {

    private final ExcelReportGenerator excelGenerator = new ExcelReportGenerator();
    private final PdfReportGenerator pdfGenerator = new PdfReportGenerator();

    // Los tiempos van en SEGUNDOS desde el arreglo del total del
    // informe (ver MonthlyReportTotalTest): se escriben como
    // "minutos * 60" para que se siga leyendo a simple vista.
    private MonthlyReport informeDeEjemplo() {
        return new MonthlyReport(
                "TechCorp Solutions",
                "Ana Fernández",
                YearMonth.of(2026, 6),
                List.of(
                        new ReportRow("Ana Fernández", LocalDate.of(2026, 6, 1),
                                LocalTime.of(9, 0), LocalTime.of(17, 30), 30, 480 * 60, false),
                        new ReportRow("Ana Fernández", LocalDate.of(2026, 6, 2),
                                LocalTime.of(9, 0), LocalTime.of(17, 0), 30, 450 * 60, false),
                        // Jornada cerrada por el sistema (Fase 9).
                        new ReportRow("Ana Fernández", LocalDate.of(2026, 6, 3),
                                LocalTime.of(9, 0), LocalTime.of(1, 0), 0, 960 * 60, true)));
    }

    // ---- Excel ----

    @Test
    @DisplayName("El Excel generado se puede volver a abrir y contiene las filas y el total")
    void excel_seGeneraYSePuedeReleer() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        MonthlyReport informe = informeDeEjemplo();

        excelGenerator.generar(informe, salida);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(salida.toByteArray()))) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(hoja.getSheetName()).contains("2026-06");

            // Fila 0: título con la empresa.
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).contains("TechCorp Solutions");

            // Fila 3: cabeceras (0 título, 1 periodo, 2 en blanco).
            assertThat(hoja.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Empleado");
            assertThat(hoja.getRow(3).getCell(5).getStringCellValue()).isEqualTo("Tiempo efectivo");

            // Fila 4: primer fichaje.
            assertThat(hoja.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Ana Fernández");
            assertThat(hoja.getRow(4).getCell(1).getStringCellValue()).isEqualTo("2026-06-01");
            assertThat(hoja.getRow(4).getCell(5).getStringCellValue()).isEqualTo("8h 00m");
            assertThat(hoja.getRow(4).getCell(6).getStringCellValue()).isEmpty();

            // Fila 6: la jornada con incidencia va marcada.
            assertThat(hoja.getRow(6).getCell(6).getStringCellValue()).isEqualTo("Cierre automático");
        }
    }

    @Test
    @DisplayName("El total del Excel suma los minutos netos de todas las filas")
    void excel_totalCuadraConLasFilas() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        MonthlyReport informe = informeDeEjemplo();

        excelGenerator.generar(informe, salida);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(salida.toByteArray()))) {
            Sheet hoja = libro.getSheetAt(0);
            // 480 + 450 + 960 = 1890 min = 31h 30m
            assertThat(informe.totalLegible()).isEqualTo("31h 30m");
            assertThat(hoja.getRow(8).getCell(5).getStringCellValue()).isEqualTo("31h 30m");
            assertThat(hoja.getRow(8).getCell(0).getStringCellValue()).contains("3 días trabajados");
        }
    }

    @Test
    @DisplayName("Un informe sin fichajes genera un Excel válido, no un fichero roto")
    void excel_sinFilas_siguesiendoValido() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        MonthlyReport vacio = new MonthlyReport("Empresa", "Nadie", YearMonth.of(2026, 1), List.of());

        excelGenerator.generar(vacio, salida);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(salida.toByteArray()))) {
            assertThat(libro.getSheetAt(0)).isNotNull();
            assertThat(vacio.totalLegible()).isEqualTo("0h 00m");
        }
    }

    // ---- PDF ----

    @Test
    @DisplayName("El PDF generado tiene la cabecera y el cierre propios de un PDF real")
    void pdf_seGeneraConEstructuraValida() {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        pdfGenerator.generar(informeDeEjemplo(), salida);

        byte[] bytes = salida.toByteArray();
        assertThat(bytes).isNotEmpty();
        // Número mágico de PDF: todo PDF empieza por "%PDF-".
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        // Y termina con el marcador de fin de fichero.
        assertThat(new String(bytes)).contains("%%EOF");
    }

    @Test
    @DisplayName("Un informe sin fichajes genera un PDF válido igualmente")
    void pdf_sinFilas_siguesiendoValido() {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        pdfGenerator.generar(new MonthlyReport("Empresa", "Nadie", YearMonth.of(2026, 1), List.of()), salida);

        assertThat(new String(salida.toByteArray(), 0, 5)).isEqualTo("%PDF-");
    }

    // ---- Cálculos del modelo ----

    @Test
    @DisplayName("Los minutos se formatean como '7h 30m', con el minuto a dos dígitos")
    void duracionLegible_formatoConDosDigitos() {
        ReportRow fila = new ReportRow("X", LocalDate.now(), LocalTime.NOON, LocalTime.MIDNIGHT, 0, 450 * 60, false);
        ReportRow filaConMinutoBajo =
                new ReportRow("X", LocalDate.now(), LocalTime.NOON, LocalTime.MIDNIGHT, 0, 425 * 60, false);

        assertThat(fila.duracionLegible()).isEqualTo("7h 30m");
        assertThat(filaConMinutoBajo.duracionLegible()).isEqualTo("7h 05m");
    }

    @Test
    @DisplayName("Los días trabajados cuentan fechas distintas, no filas")
    void diasTrabajados_cuentaFechasDistintas() {
        MonthlyReport dosFichajesElMismoDia = new MonthlyReport("E", "X", YearMonth.of(2026, 6), List.of(
                new ReportRow("X", LocalDate.of(2026, 6, 1), LocalTime.of(9, 0), LocalTime.of(13, 0), 0, 240 * 60, false),
                new ReportRow("X", LocalDate.of(2026, 6, 1), LocalTime.of(15, 0), LocalTime.of(19, 0), 0, 240 * 60, false)));

        assertThat(dosFichajesElMismoDia.diasTrabajados()).isEqualTo(1);
        assertThat(dosFichajesElMismoDia.totalLegible()).isEqualTo("8h 00m");
    }

    @Test
    @DisplayName("Las incidencias se cuentan aparte para poder avisar en el informe")
    void incidencias_seCuentan() {
        assertThat(informeDeEjemplo().incidencias()).isEqualTo(1);
    }
}
