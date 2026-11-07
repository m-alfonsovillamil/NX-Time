package com.nxtime.nxtime.report;

import com.nxtime.nxtime.config.CondicionesDelServicio;
import com.nxtime.nxtime.config.Marca;
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
import java.time.ZoneId;
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
 *
 * Con los colores de la marca ({@link Marca}) desde noviembre de 2026: antes
 * llevaba el azul genérico de los correos, distinto del teal de la app.
 */
@Component
public class PdfReportGenerator {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /*
     * La zona del documento es la de la empresa, que viaja en el informe
     * (ADR 032), y nunca la de la máquina: el contenedor de producción corre
     * en UTC, y un informe generado de madrugada en España llevaba en el pie
     * la fecha del día ANTERIOR.
     */
    private static final String[] CABECERAS = {"Fecha", "Entrada", "Salida", "Pausa", "Tiempo efectivo"};

    private static final Color PRIMARIO = Marca.awt(Marca.PRIMARIO);
    private static final Color BORDE = Marca.awt(Marca.BORDE);
    private static final Color FONDO = Marca.awt(Marca.FONDO);

    private static final Font MARCA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, PRIMARIO);
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Marca.awt(Marca.TEXTO));
    private static final Font SUBTITULO =
            FontFactory.getFont(FontFactory.HELVETICA, 10, Marca.awt(Marca.TEXTO_SECUNDARIO));
    private static final Font CABECERA_TABLA =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Marca.awt(Marca.SOBRE_PRIMARIO));
    private static final Font CELDA = FontFactory.getFont(FontFactory.HELVETICA, 9, Marca.awt(Marca.TEXTO));
    private static final Font CELDA_AVISO =
            FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Marca.awt(Marca.ERROR));
    private static final Font TOTAL =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Marca.awt(Marca.SOBRE_CONTENEDOR_PRIMARIO));
    private static final Font PIE = FontFactory.getFont(FontFactory.HELVETICA, 8, Marca.awt(Marca.TEXTO_SECUNDARIO));

    private final CondicionesDelServicio servicio;

    public PdfReportGenerator(CondicionesDelServicio servicio) {
        this.servicio = servicio;
    }

    public void generar(MonthlyReport informe, OutputStream salida) {
        Document documento = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(documento, salida);
        documento.open();

        documento.add(new Paragraph(servicio.producto(), MARCA));
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
        documento.add(bloqueDeFirma(informe.firma(), informe.zona()));
        documento.add(pieLegal(informe.zona()));

        documento.close();
    }

    private Paragraph espacio() {
        Paragraph espacio = new Paragraph(" ");
        espacio.setSpacingAfter(8);
        return espacio;
    }

    private PdfPTable tablaDeJornadas(MonthlyReport informe) {
        // La modalidad (ADR 055), solo si alguna jornada la tiene: a una
        // empresa que no la pregunta no le sirve una columna vacía.
        boolean conModalidad = informe.filas().stream().anyMatch(fila -> fila.modalidad() != null);
        String[] cabeceras = conModalidad
                ? java.util.stream.Stream.concat(java.util.Arrays.stream(CABECERAS), java.util.stream.Stream.of("Modalidad"))
                        .toArray(String[]::new)
                : CABECERAS;
        PdfPTable tabla = new PdfPTable(cabeceras.length);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(10);

        for (String cabecera : cabeceras) {
            PdfPCell celda = new PdfPCell(new Phrase(cabecera, CABECERA_TABLA));
            celda.setBackgroundColor(PRIMARIO);
            celda.setBorderColor(PRIMARIO);
            celda.setPadding(6);
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(celda);
        }

        int numero = 0;
        for (ReportRow fila : informe.filas()) {
            // Las jornadas que cerró el sistema por falta de fichaje de
            // salida (Fase 9) se marcan en rojo y en cursiva: el dato es
            // menos fiable y quien firme el documento debe verlo.
            Font fuente = fila.incidencia() ? CELDA_AVISO : CELDA;
            // Filas alternas con el fondo de la marca: un mes son treinta
            // filas, y sin ellas la vista se salta de una a otra.
            Color fondo = numero++ % 2 == 1 ? FONDO : null;
            tabla.addCell(celdaTexto(fila.fecha().format(FECHA), fuente, fondo));
            tabla.addCell(celdaTexto(fila.horaEntrada().toString(), fuente, fondo));
            tabla.addCell(celdaTexto(fila.horaSalida().toString(), fuente, fondo));
            tabla.addCell(celdaTexto(fila.minutosPausa() + " min", fuente, fondo));
            tabla.addCell(celdaTexto(fila.duracionLegible(), fuente, fondo));
            if (conModalidad) {
                tabla.addCell(celdaTexto(com.nxtime.nxtime.domain.Modalidad.texto(fila.modalidad()), fuente, fondo));
            }
        }

        return tabla;
    }

    private PdfPCell celdaTexto(String texto, Font fuente, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setPadding(4);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda.setBorderColor(BORDE);
        if (fondo != null) {
            celda.setBackgroundColor(fondo);
        }
        return celda;
    }

    /** El total, en el contenedor de la marca: es la cifra que se busca primero. */
    private PdfPTable resumen(MonthlyReport informe) {
        PdfPTable caja = new PdfPTable(1);
        caja.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell(new Phrase(
                "Días trabajados: " + informe.diasTrabajados()
                        + "        Total del periodo: " + informe.totalLegible(), TOTAL));
        celda.setBackgroundColor(Marca.awt(Marca.CONTENEDOR_PRIMARIO));
        celda.setBorder(org.openpdf.text.Rectangle.NO_BORDER);
        celda.setPadding(8);
        caja.addCell(celda);
        return caja;
    }

    private Paragraph avisoDeIncidencias(MonthlyReport informe) {
        Paragraph aviso = new Paragraph(
                informe.incidencias() + " jornada(s) marcadas en rojo se cerraron automáticamente por no "
                        + "constar el fichaje de salida; sus horas son estimadas y están pendientes de corrección.",
                CELDA_AVISO);
        aviso.setSpacingBefore(6);
        return aviso;
    }

    /**
     * Las dos celdas de firma. Sin firma electrónica, en blanco para firmar a
     * mano, exactamente como antes de la Fase B3. Con ella, la celda del
     * trabajador dice quién firmó, cuándo, y el principio de la huella: con
     * eso cualquiera puede pedir que se compruebe que el PDF dice lo firmado.
     */
    private PdfPTable bloqueDeFirma(MonthlyReport.FirmaDelInforme firma, ZoneId zona) {
        PdfPTable firmas = new PdfPTable(2);
        firmas.setWidthPercentage(100);
        firmas.setSpacingBefore(30);

        if (firma == null) {
            firmas.addCell(celdaDeFirma("Firma del trabajador"));
            firmas.addCell(celdaDeFirma("Firma de la empresa"));
            return firmas;
        }
        firmas.addCell(celdaDeFirma("Firmado electrónicamente por " + firma.firmadaPor()
                + " el " + FECHA_Y_HORA.format(firma.firmadaEn().atZone(zona))
                + "\nHuella " + firma.hash().substring(0, 16) + "…"
                + "\n(firma de aceptación, no firma electrónica cualificada)"
                + "\n\nFirma del trabajador"));
        firmas.addCell(celdaDeFirma(firma.visadaPor() == null
                ? "Firma de la empresa"
                : "Visado por " + firma.visadaPor() + " el "
                        + FECHA_Y_HORA.format(firma.visadaEn().atZone(zona)) + "\n\nFirma de la empresa"));
        return firmas;
    }

    private PdfPCell celdaDeFirma(String etiqueta) {
        PdfPCell celda = new PdfPCell(new Phrase("\n\n\n" + etiqueta, CELDA));
        celda.setBorder(org.openpdf.text.Rectangle.TOP);
        celda.setBorderColor(BORDE);
        celda.setPaddingTop(6);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        return celda;
    }

    /**
     * Qué día es hoy en la empresa, no en la máquina que genera el documento.
     *
     * Paquete y no privado para poder probarlo: el fallo solo se ve entre
     * medianoche y las dos de la mañana, y montar ese momento es mucho más
     * fácil sobre este método que sobre el PDF entero.
     */
    LocalDate hoyEn(ZoneId zona) {
        return LocalDate.now(zona);
    }

    private Paragraph pieLegal(ZoneId zona) {
        Paragraph pie = new Paragraph(
                "Documento generado por " + servicio.producto() + " el " + hoyEn(zona).format(FECHA)
                        + ". Registro de jornada conforme al RD-ley 8/2019; conservar durante cuatro años.",
                PIE);
        pie.setSpacingBefore(20);
        return pie;
    }
}
