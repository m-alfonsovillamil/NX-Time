package com.nxtime.nxtime.report;

import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.ComplaintCategory;
import com.nxtime.nxtime.dto.PersonalDataExport;
import com.nxtime.nxtime.service.impl.PersonalDataExportServiceImpl;
import java.awt.Color;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
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
import org.springframework.stereotype.Component;

/**
 * La exportación de datos personales, para leerla (RGPD, art. 15).
 *
 * El JSON es el formato que exige la portabilidad; este es el que entiende
 * una persona sin herramientas. Tienen <b>el mismo contenido</b> a propósito:
 * se generan del mismo {@link PersonalDataExport}, así que no puede haber algo
 * en uno que falte en el otro.
 *
 * Las horas van en hora de España, no en UTC como en el JSON: aquí se leen.
 */
@Component
public class PersonalDataPdfGenerator {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final Color AZUL = new Color(31, 111, 235);
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, AZUL);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font SUAVE = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font CELDA = FontFactory.getFont(FontFactory.HELVETICA, 8);

    public void generar(PersonalDataExport datos, OutputStream salida) {
        Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(documento, salida);
        documento.open();

        documento.add(new Paragraph("Tus datos en NX Time", TITULO));
        documento.add(new Paragraph("Generado el " + fechaHora(datos.generadoEn())
                + ". Contiene lo mismo que el fichero JSON de la misma exportación.", SUAVE));

        PersonalDataExport.Persona p = datos.persona();
        seccion(documento, "Datos personales");
        PdfPTable ficha = new PdfPTable(new float[] {1.2f, 3f});
        ficha.setWidthPercentage(100);
        fila(ficha, "Nombre", texto(p.nombre()) + " " + texto(p.apellidos()));
        fila(ficha, "Correo", p.email());
        fila(ficha, "Empresa", texto(p.empresa()));
        fila(ficha, "Rol", p.rol());
        fila(ficha, "Departamento", texto(p.departamento()));
        fila(ficha, "Puesto", texto(p.puesto()));
        fila(ficha, "Fecha de nacimiento", p.fechaNacimiento() != null ? p.fechaNacimiento().format(FECHA) : "—");
        fila(ficha, "Jornada semanal", p.horasSemanales() != null ? p.horasSemanales().stripTrailingZeros().toPlainString() + " h" : "—");
        fila(ficha, "Cuenta", p.activo() ? "Activa" : "De baja desde " + fechaHora(p.fechaBaja()));
        documento.add(ficha);

        tabla(documento, "Fichajes (" + datos.fichajes().size() + ")", datos.fichajes(),
                new String[] {"Fecha", "Entrada", "Salida", "Pausa", "Trabajado", "Observaciones"},
                f -> new String[] {
                        fecha(f.horaEntrada()), hora(f.horaEntrada()), hora(f.horaSalida()),
                        minutos(f.segundosPausa()), neto(f), observaciones(f)});

        tabla(documento, "Pausas añadidas a posteriori", datos.pausasAnadidas(),
                new String[] {"Fecha", "Desde", "Hasta", "Motivo", "Cómo entró"},
                pa -> new String[] {
                        fecha(pa.inicio()), hora(pa.inicio()), hora(pa.fin()), pa.motivo(),
                        (pa.porAprobacion() ? "Aprobada" : "Directa") + (pa.anulada() ? " (deshecha)" : "")});

        tabla(documento, "Ausencias", datos.ausencias(),
                new String[] {"Desde", "Hasta", "Tipo", "Estado", "Motivo"},
                a -> new String[] {
                        a.fechaInicio().format(FECHA), a.fechaFin().format(FECHA), etiquetaAusencia(a.tipo()),
                        a.estado(), texto(a.motivo())});

        tabla(documento, "Vacaciones por año", datos.saldosVacaciones(),
                new String[] {"Año", "Días"},
                v -> new String[] {String.valueOf(v.anio()), String.valueOf(v.diasTotales())});

        tabla(documento, "Correcciones que has pedido", datos.correccionesPedidas(),
                new String[] {"Pedida", "Propuesta", "Motivo", "Estado"},
                c -> new String[] {
                        fechaHora(c.creadoEn()),
                        hora(c.horaEntradaPropuesta()) + "–" + hora(c.horaSalidaPropuesta())
                                + (c.pausaInicioPropuesta() != null
                                        ? " (pausa " + hora(c.pausaInicioPropuesta()) + "–" + hora(c.pausaFinPropuesta()) + ")"
                                        : ""),
                        c.motivo(), c.estado()});

        tabla(documento, "Horas extra detectadas", datos.horasExtra(),
                new String[] {"Fecha", "Tipo", "Exceso", "Estado", "Justificación"},
                h -> new String[] {
                        h.fecha().format(FECHA), h.tipo(), h.minutosExtra() + " min", h.estado(),
                        texto(h.justificacion())});

        tabla(documento, "Proyectos", datos.proyectos(),
                new String[] {"Código", "Nombre", "Desde", "Hasta"},
                pr -> new String[] {pr.codigo(), pr.nombre(), fechaLocal(pr.desde()), fechaLocal(pr.hasta())});

        tabla(documento, "Cuadrantes de horario", datos.cuadrantes(),
                new String[] {"Plantilla", "Desde", "Hasta"},
                c -> new String[] {c.plantilla(), fechaLocal(c.desde()), fechaLocal(c.hasta())});

        tabla(documento, "Días fuera del cuadrante", datos.excepcionesDeCuadrante(),
                new String[] {"Fecha", "Tipo", "Horario", "Motivo"},
                e -> new String[] {
                        fechaLocal(e.fecha()), e.tipo(),
                        e.horaInicio() != null ? e.horaInicio() + "–" + e.horaFin() : "",
                        texto(e.motivo())});

        tabla(documento, "Incidencias de cuadrante", datos.incidenciasDeCuadrante(),
                new String[] {"Fecha", "Tipo", "Minutos", "Estado", "Explicación"},
                i -> new String[] {
                        fechaLocal(i.fecha()), i.tipo(), String.valueOf(i.minutos()), i.estado(),
                        texto(i.justificacion())});

        tabla(documento, "Avisos recibidos (" + datos.avisos().size() + ")", datos.avisos(),
                new String[] {"Fecha", "Aviso", "Leído"},
                av -> new String[] {fechaHora(av.creadoEn()), av.titulo(), av.leido() ? "Sí" : "No"});

        tabla(documento, "Ficheros adjuntos", datos.adjuntos(),
                new String[] {"Tipo", "Nombre", "Tamaño", "Subido", "Vigente"},
                ad -> new String[] {
                        ad.tipo(), ad.nombre(), (ad.tamanoBytes() / 1024) + " KB", fechaHora(ad.subidoEn()),
                        ad.vigente() ? "Sí" : "No"});

        tabla(documento, "Candidaturas", datos.candidaturas(),
                new String[] {"Oferta", "Presentada", "Estado"},
                ca -> new String[] {ca.oferta(), fechaHora(ca.creadoEn()), ca.estado()});

        tabla(documento, "Denuncias presentadas identificándote", datos.denunciasIdentificadas(),
                new String[] {"Presentada", "Categoría", "Estado"},
                d -> new String[] {fechaHora(d.creadoEn()), etiquetaDenuncia(d.categoria()), d.estado()});

        seccion(documento, "Notas sobre esta exportación");
        for (String nota : datos.notas()) {
            // La nota de las horas en UTC es cierta en el JSON y falsa aquí,
            // donde van en hora de España. Se vio al mirar el PDF generado.
            String texto = PersonalDataExportServiceImpl.NOTA_HORAS_EN_UTC.equals(nota)
                    ? "Las horas van en hora de España. En el fichero JSON van en UTC (formato ISO 8601)."
                    : nota;
            documento.add(new Paragraph("• " + texto, NORMAL));
        }

        documento.close();
    }

    // ------------------------------------------------------------------

    private <T> void tabla(Document documento, String titulo, List<T> filas, String[] cabeceras,
                           Function<T, String[]> celdas) {
        seccion(documento, titulo);
        if (filas.isEmpty()) {
            // "Sin datos" y no omitir la sección: que falte un apartado se lee
            // como que no se ha incluido, no como que no hay nada.
            documento.add(new Paragraph("Sin datos.", SUAVE));
            return;
        }
        PdfPTable tabla = new PdfPTable(cabeceras.length);
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String cabecera : cabeceras) {
            PdfPCell celda = new PdfPCell(new Phrase(cabecera, CABECERA));
            celda.setBackgroundColor(AZUL);
            celda.setPadding(4);
            tabla.addCell(celda);
        }
        for (T fila : filas) {
            for (String valor : celdas.apply(fila)) {
                PdfPCell celda = new PdfPCell(new Phrase(texto(valor), CELDA));
                celda.setPadding(3);
                celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
                tabla.addCell(celda);
            }
        }
        documento.add(tabla);
    }

    private void seccion(Document documento, String titulo) {
        Paragraph parrafo = new Paragraph(titulo, SECCION);
        parrafo.setSpacingBefore(14);
        parrafo.setSpacingAfter(6);
        documento.add(parrafo);
    }

    private void fila(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell izquierda = new PdfPCell(new Phrase(etiqueta, CABECERA));
        izquierda.setBackgroundColor(AZUL);
        izquierda.setPadding(4);
        tabla.addCell(izquierda);
        PdfPCell derecha = new PdfPCell(new Phrase(texto(valor), CELDA));
        derecha.setPadding(4);
        tabla.addCell(derecha);
    }

    private static String neto(PersonalDataExport.Fichaje f) {
        if (f.horaSalida() == null) {
            return "En curso";
        }
        long segundos = Duration.between(f.horaEntrada(), f.horaSalida()).getSeconds() - f.segundosPausa();
        return minutos(Math.max(0, segundos));
    }

    private static String observaciones(PersonalDataExport.Fichaje f) {
        if (f.anulado()) {
            return "Sustituido por una corrección";
        }
        if (f.corrigeAlFichaje() != null) {
            return "Corrección aprobada";
        }
        return f.jornadaIncompleta() ? "Cerrada por el sistema" : "";
    }

    private static String minutos(long segundos) {
        long total = segundos / 60;
        return String.format("%dh %02dm", total / 60, total % 60);
    }

    private static String etiquetaAusencia(String tipo) {
        try {
            return AbsenceType.valueOf(tipo).getEtiqueta();
        } catch (IllegalArgumentException e) {
            return tipo;
        }
    }

    private static String etiquetaDenuncia(String categoria) {
        try {
            return ComplaintCategory.valueOf(categoria).getEtiqueta();
        } catch (IllegalArgumentException e) {
            return categoria;
        }
    }

    private static String fecha(Instant instante) {
        return instante == null ? "—" : instante.atZone(MADRID).format(FECHA);
    }

    private static String hora(Instant instante) {
        return instante == null ? "—" : instante.atZone(MADRID).format(HORA);
    }

    private static String fechaHora(Instant instante) {
        return instante == null ? "—" : instante.atZone(MADRID).format(FECHA_HORA);
    }

    private static String fechaLocal(LocalDate fecha) {
        return fecha == null ? "—" : fecha.format(FECHA);
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }
}
