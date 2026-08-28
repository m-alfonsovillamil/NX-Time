package com.nxtime.nxtime.report;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Genera el informe mensual individual en PDF (Fase 10).
 *
 * Es el documento que exige el registro horario español (RD-ley
 * 8/2019): el detalle de las jornadas de UN empleado en UN mes, con su
 * total y un espacio para la firma. Por eso es individual y no de toda
 * la empresa -- para eso está el Excel.
 *
 * Se usa OpenPDF (LGPL/MPL) y no iText 7, cuya licencia AGPL obligaría
 * a liberar el proyecto entero o a comprar licencia. OpenPDF es un fork
 * de iText 2 y hasta su versión 2.x conservaba el paquete original
 * {@code com.lowagie}; desde la 3.0 pasó a {@code org.openpdf}, que es
 * el que se usa aquí -- conviene saberlo porque casi todos los ejemplos
 * que circulan por internet siguen usando el antiguo.
 *
 * Escribe directamente en el OutputStream de la respuesta, igual que
 * {@link ExcelReportGenerator}.
 */
@Component
public class PdfReportGenerator {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] CABECERAS = {"Fecha", "Entrada", "Salida", "Pausa", "Tiempo efectivo"};

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
    private static final Font CABECERA_TABLA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font CELDA = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font CELDA_AVISO = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.RED);
    private static final Font TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font PIE = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

    public void generar(MonthlyReport informe, OutputStream salida) {
        Document documento = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(documento, salida);
        documento.open();

        documento.add(new Paragraph("Registro horario mensual", TITULO));
        documento.add(new Paragraph(informe.nombreEmpresa(), SUBTITULO));
        documento.add(new Paragraph(
                "Empleado: " + informe.nombreEmpleado() + "   |   Periodo: " + informe.mes(), SUBTITULO));
        documento.add(espacio());

        documento.add(tablaDeJornadas(informe));
        documento.add(espacio());

        documento.add(resumen(informe));
        if (informe.incidencias() > 0) {
            documento.add(avisoDeIncidencias(informe));
        }

        documento.add(espacio());
        documento.add(bloqueDeFirma());
        documento.add(pieLegal());

        documento.close();
    }

    private Paragraph espacio() {
        Paragraph espacio = new Paragraph(" ");
        espacio.setSpacingAfter(8);
        return espacio;
    }

    private PdfPTable tablaDeJornadas(MonthlyReport informe) {
        PdfPTable tabla = new PdfPTable(CABECERAS.length);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(10);

        for (String cabecera : CABECERAS) {
            PdfPCell celda = new PdfPCell(new Phrase(cabecera, CABECERA_TABLA));
            celda.setBackgroundColor(new Color(31, 111, 235));
            celda.setPadding(5);
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(celda);
        }

        for (ReportRow fila : informe.filas()) {
            // Las jornadas que cerró el sistema por falta de fichaje de
            // salida (Fase 9) se marcan en rojo y en cursiva: el dato es
            // menos fiable y quien firme el documento debe verlo.
            Font fuente = fila.incidencia() ? CELDA_AVISO : CELDA;
            tabla.addCell(celdaTexto(fila.fecha().format(FECHA), fuente));
            tabla.addCell(celdaTexto(fila.horaEntrada().toString(), fuente));
            tabla.addCell(celdaTexto(fila.horaSalida().toString(), fuente));
            tabla.addCell(celdaTexto(fila.minutosPausa() + " min", fuente));
            tabla.addCell(celdaTexto(fila.duracionLegible(), fuente));
        }

        return tabla;
    }

    private PdfPCell celdaTexto(String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setPadding(4);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        return celda;
    }

    private Paragraph resumen(MonthlyReport informe) {
        return new Paragraph(
                "Días trabajados: " + informe.diasTrabajados()
                        + "        Total del periodo: " + informe.totalLegible(), TOTAL);
    }

    private Paragraph avisoDeIncidencias(MonthlyReport informe) {
        Paragraph aviso = new Paragraph(
                informe.incidencias() + " jornada(s) marcadas en rojo se cerraron automáticamente por no "
                        + "constar el fichaje de salida; sus horas son estimadas y están pendientes de corrección.",
                CELDA_AVISO);
        aviso.setSpacingBefore(6);
        return aviso;
    }

    private PdfPTable bloqueDeFirma() {
        PdfPTable firmas = new PdfPTable(2);
        firmas.setWidthPercentage(100);
        firmas.setSpacingBefore(30);

        firmas.addCell(celdaDeFirma("Firma del trabajador"));
        firmas.addCell(celdaDeFirma("Firma de la empresa"));
        return firmas;
    }

    private PdfPCell celdaDeFirma(String etiqueta) {
        PdfPCell celda = new PdfPCell(new Phrase("\n\n\n" + etiqueta, CELDA));
        celda.setBorder(org.openpdf.text.Rectangle.TOP);
        celda.setPaddingTop(6);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        return celda;
    }

    private Paragraph pieLegal() {
        Paragraph pie = new Paragraph(
                "Documento generado por NX Time el " + LocalDate.now().format(FECHA)
                        + ". Registro de jornada conforme al RD-ley 8/2019; conservar durante cuatro años.",
                PIE);
        pie.setSpacingBefore(20);
        return pie;
    }
}
