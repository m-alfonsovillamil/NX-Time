package com.nxtime.nxtime.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;
import java.util.TimeZone;
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

    /**
     * Un informe grande cruza muchas veces la ventana de SXSSF (Fase A8).
     *
     * Con {@code XSSFWorkbook} el libro entero se construía en memoria antes
     * de escribir el primer byte, lo que hacía del {@code
     * StreamingResponseBody} del controlador una contradicción. Con SXSSF solo
     * conviven cien filas, y este test comprueba lo único que se puede
     * comprobar de forma fiable: que el fichero resultante sigue siendo un
     * XLSX válido y completo cuando las filas ya no caben en la ventana.
     *
     * No mide memoria a propósito: un umbral de heap en un test es
     * intermitente por naturaleza --depende del GC, del recolector y de la
     * máquina-- y un test que falla a veces se acaba ignorando siempre.
     */
    @Test
    @DisplayName("Un informe de miles de filas sigue produciendo un XLSX válido y completo")
    void excel_muchasFilas_siguenSiendoValidas() throws Exception {
        int filas = 5_000;
        List<ReportRow> lineas = IntStream.rangeClosed(1, filas)
                .mapToObj(i -> new ReportRow("Empleado " + i, LocalDate.of(2026, 6, 1).plusDays(i % 28),
                        LocalTime.of(9, 0), LocalTime.of(17, 0), 30, 450 * 60, false))
                .toList();
        MonthlyReport informe = new MonthlyReport(
                "Empresa grande", "Todos", YearMonth.of(2026, 6), lineas);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        excelGenerator.generar(informe, salida);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(salida.toByteArray()))) {
            Sheet hoja = libro.getSheetAt(0);
            // Las cabeceras están en la fila 3, así que los datos van de la 4
            // en adelante. La última fila de datos tiene que estar entera: si
            // el volcado a disco se hubiera comido algo, faltaría el final.
            assertThat(hoja.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Empleado 1");
            assertThat(hoja.getRow(3 + filas).getCell(0).getStringCellValue())
                    .isEqualTo("Empleado " + filas);
            assertThat(hoja.getRow(3 + filas).getCell(5).getStringCellValue()).isEqualTo("7h 30m");
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

    /** El texto de la primera página, como lo leería una persona. */
    private String textoDelPdf(MonthlyReport informe) throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        pdfGenerator.generar(informe, salida);
        try (org.openpdf.text.pdf.PdfReader lector = new org.openpdf.text.pdf.PdfReader(salida.toByteArray())) {
            return new org.openpdf.text.pdf.parser.PdfTextExtractor(lector).getTextFromPage(1);
        }
    }

    @Test
    @DisplayName("Con firma electrónica, el PDF dice quién firmó, cuándo, la huella y que no es cualificada")
    void pdf_conFirma_laCita() throws Exception {
        MonthlyReport base = informeDeEjemplo();
        MonthlyReport firmado = new MonthlyReport(base.nombreEmpresa(), base.nombreEmpleado(), base.mes(), base.filas(),
                new MonthlyReport.FirmaDelInforme("Ana", java.time.Instant.parse("2026-07-01T08:30:00Z"),
                        "3f9c1d2a7b8e4f60" + "0".repeat(48), "Elena",
                        java.time.Instant.parse("2026-07-02T10:00:00Z")));

        String texto = textoDelPdf(firmado);

        assertThat(texto).contains("Firmado electrónicamente por Ana el 01/07/2026 10:30")
                .contains("3f9c1d2a7b8e4f60")
                .contains("no firma electrónica cualificada")
                .contains("Visado por Elena el 02/07/2026 12:00");
    }

    @Test
    @DisplayName("Sin firma, el bloque de firmas es el de siempre: dos huecos para firmar a mano")
    void pdf_sinFirma_comoAntes() throws Exception {
        String texto = textoDelPdf(informeDeEjemplo());

        assertThat(texto).contains("Firma del trabajador").contains("Firma de la empresa")
                .doesNotContain("Firmado electrónicamente").doesNotContain("Visado");
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
    /*
     * El pie del PDF ("Documento generado el ...") usaba LocalDate.now()
     * sin zona, es decir la de la máquina. El contenedor de producción va
     * en UTC, así que un informe generado entre medianoche y las dos de la
     * mañana en España llevaba la fecha del día ANTERIOR. Era el único de
     * los 34 sitios del proyecto que no nombraba la zona.
     *
     * Para que el test sea determinista se elige una zona lejana en la que
     * AHORA MISMO sea otro día distinto que en España: doce horas por
     * delante si aquí es tarde, trece por detrás si es temprano.
     */
    @Test
    @DisplayName("La fecha del pie es la de España aunque la máquina vaya en otra zona")
    void pieLegal_usaLaFechaDeEspana() {
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        LocalDate hoyEnEspana = LocalDate.now(madrid);
        ZoneId otroDia = LocalTime.now(madrid).getHour() >= 12
                ? ZoneId.of("Pacific/Kiritimati")   // UTC+14: ya es mañana
                : ZoneId.of("Pacific/Pago_Pago");   // UTC-11: todavía es ayer
        assertThat(LocalDate.now(otroDia))
                .as("la zona elegida tiene que estar en otro día, o el test no probaría nada")
                .isNotEqualTo(hoyEnEspana);

        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(otroDia));
            assertThat(pdfGenerator.hoyEnEspana()).isEqualTo(hoyEnEspana);
        } finally {
            TimeZone.setDefault(original);
        }
    }

}
