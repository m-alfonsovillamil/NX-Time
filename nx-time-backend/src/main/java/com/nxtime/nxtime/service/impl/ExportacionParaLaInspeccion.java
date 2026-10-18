package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.DatoConsultado;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.report.AbsenteeismCsv;
import com.nxtime.nxtime.report.ExcelReportGenerator;
import com.nxtime.nxtime.report.MonthlyReport;
import com.nxtime.nxtime.report.PdfReportGenerator;
import com.nxtime.nxtime.service.ReportService;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * La exportación «para la Inspección o la representación legal» (plan J, fase
 * J7): un ZIP con todo el registro de jornada de un periodo, en formatos que
 * se pueden tratar.
 *
 * <ul>
 *   <li>{@code horas-AAAA-MM.xlsx}: todas las jornadas del mes, de todos;</li>
 *   <li>{@code registros/AAAA-MM/persona.pdf}: el registro mensual oficial de
 *       cada persona que fichó ese mes, con su firma si la hay;</li>
 *   <li>{@code correcciones.csv}: la traza de auditoría de cada cambio, con
 *       quién lo hizo, cuándo y por qué;</li>
 *   <li>{@code firmas.csv}: las firmas mensuales, vigentes e invalidadas;</li>
 *   <li>{@code LEEME.txt}: qué es cada cosa.</li>
 * </ul>
 *
 * <b>Se prepara entero antes de escribir el primer byte</b>: una vez empezado
 * el ZIP ya se ha mandado el 200, y un fallo a medias no podría convertirse en
 * un error que se entienda (ver {@code ReportController}). Lo que ocupa son
 * filas de jornadas: con los volúmenes de hoy, poco. Por eso se limita a doce
 * meses.
 *
 * La traza no lleva la IP de cada fichaje: no la pide el art. 34.9 ET, y es un
 * dato que la Inspección puede pedir aparte si lo necesita (minimización).
 */
@Component
public class ExportacionParaLaInspeccion {

    static final int MAXIMO_DE_MESES = 12;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReportService informes;
    private final ExcelReportGenerator excel;
    private final PdfReportGenerator pdf;
    private final JdbcTemplate jdbc;
    private final RegistroDeAccesos accesos;

    public ExportacionParaLaInspeccion(ReportService informes, ExcelReportGenerator excel, PdfReportGenerator pdf,
            JdbcTemplate jdbc, RegistroDeAccesos accesos) {
        this.informes = informes;
        this.excel = excel;
        this.pdf = pdf;
        this.jdbc = jdbc;
        this.accesos = accesos;
    }

    /** Lo que va en el ZIP, ya consultado. */
    public record Preparada(List<MonthlyReport> deLaEmpresa, List<MonthlyReport> dePersonas, String correcciones,
            String firmas, String leeme) {
    }

    public Preparada preparar(User actor, YearMonth desde, YearMonth hasta) {
        if (hasta.isBefore(desde)) {
            throw new BusinessException("El mes final no puede ser anterior al inicial.", HttpStatus.BAD_REQUEST);
        }
        if (desde.plusMonths(MAXIMO_DE_MESES - 1L).isBefore(hasta)) {
            throw new BusinessException("Como mucho doce meses por exportación.", HttpStatus.BAD_REQUEST);
        }
        ZoneId zona = actor.zona();
        List<MonthlyReport> deLaEmpresa = new ArrayList<>();
        List<MonthlyReport> dePersonas = new ArrayList<>();
        for (YearMonth mes = desde; !mes.isAfter(hasta); mes = mes.plusMonths(1)) {
            deLaEmpresa.add(informes.informeDeEmpresa(actor.getEmail(), mes));
            dePersonas.addAll(informes.informesDeLaPlantilla(actor.getEmail(), mes));
        }
        Instant inicio = desde.atDay(1).atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant();

        accesos.anotar(actor, DatoConsultado.EXPORTACION, null,
                "Exportación para la Inspección, " + desde + " a " + hasta);
        return new Preparada(deLaEmpresa, dePersonas,
                correcciones(actor.getEmpresa().getId(), inicio, fin, zona),
                firmas(actor.getEmpresa().getId(), desde, hasta, zona),
                leeme(actor, desde, hasta));
    }

    public void escribir(Preparada preparada, OutputStream salida) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            OutputStream sinCerrar = new SinCerrar(zip);
            entrada(zip, "LEEME.txt", preparada.leeme().getBytes(StandardCharsets.UTF_8));
            for (MonthlyReport mes : preparada.deLaEmpresa()) {
                zip.putNextEntry(new ZipEntry("horas-" + mes.mes() + ".xlsx"));
                excel.generar(mes, sinCerrar);
                zip.closeEntry();
            }
            // Dos personas con el mismo nombre no pueden pisarse: un ZIP no
            // admite dos entradas iguales, y la segunda haría fallar todo.
            Set<String> usados = new HashSet<>();
            for (MonthlyReport persona : preparada.dePersonas()) {
                String base = "registros/" + persona.mes() + "/" + nombreDeFichero(persona.nombreEmpleado());
                String nombre = base + ".pdf";
                for (int n = 2; !usados.add(nombre); n++) {
                    nombre = base + "-" + n + ".pdf";
                }
                zip.putNextEntry(new ZipEntry(nombre));
                pdf.generar(persona, sinCerrar);
                zip.closeEntry();
            }
            entrada(zip, "correcciones.csv", conBom(preparada.correcciones()));
            entrada(zip, "firmas.csv", conBom(preparada.firmas()));
        }
    }

    private String correcciones(long empresaId, Instant inicio, Instant fin, ZoneId zona) {
        StringBuilder csv = new StringBuilder();
        linea(csv, List.of("Movimiento", "Fecha", "Persona", "Acción", "Hecho por", "Motivo", "Antes", "Después"));
        jdbc.query("""
                SELECT a.id, a.fecha_hora, a.accion, a.motivo,
                       a.valor_anterior::text AS antes, a.valor_nuevo::text AS despues,
                       trim(p.nombre || ' ' || coalesce(p.apellidos, '')) AS persona,
                       trim(m.nombre || ' ' || coalesce(m.apellidos, '')) AS autor
                  FROM auditoria_fichaje a
                  JOIN registros r ON r.id = a.registro_id
                  JOIN usuarios p ON p.id = a.usuario_id
                  JOIN usuarios m ON m.id = a.modificado_por_id
                 WHERE r.empresa_id = ? AND a.fecha_hora >= ? AND a.fecha_hora < ?
                 ORDER BY a.id
                """, fila -> {
                    linea(csv, List.of(
                            String.valueOf(fila.getLong("id")),
                            FECHA_HORA.format(fila.getTimestamp("fecha_hora").toInstant().atZone(zona)),
                            fila.getString("persona"),
                            fila.getString("accion"),
                            fila.getString("autor"),
                            vacio(fila.getString("motivo")),
                            vacio(fila.getString("antes")),
                            vacio(fila.getString("despues"))));
                }, empresaId, Timestamp.from(inicio), Timestamp.from(fin));
        return csv.toString();
    }

    private String firmas(long empresaId, YearMonth desde, YearMonth hasta, ZoneId zona) {
        StringBuilder csv = new StringBuilder();
        linea(csv, List.of("Persona", "Mes", "Estado", "Firmada", "Huella", "Visada por", "Visada", "Invalidada", "Motivo"));
        jdbc.query("""
                SELECT f.anio, f.mes, f.estado, f.firmada_en, f.hash, f.visada_en, f.invalidada_en, f.motivo_invalidacion,
                       trim(u.nombre || ' ' || coalesce(u.apellidos, '')) AS persona,
                       trim(v.nombre || ' ' || coalesce(v.apellidos, '')) AS visada_por
                  FROM firmas_mensuales f
                  JOIN usuarios u ON u.id = f.usuario_id
                  LEFT JOIN usuarios v ON v.id = f.visada_por_id
                 WHERE f.empresa_id = ? AND (f.anio * 100 + f.mes) BETWEEN ? AND ?
                 ORDER BY f.anio, f.mes, persona, f.id
                """, fila -> {
                    linea(csv, List.of(
                            fila.getString("persona"),
                            YearMonth.of(fila.getInt("anio"), fila.getInt("mes")).toString(),
                            fila.getString("estado"),
                            fecha(fila.getTimestamp("firmada_en"), zona),
                            fila.getString("hash"),
                            vacio(fila.getString("visada_por")),
                            fecha(fila.getTimestamp("visada_en"), zona),
                            fecha(fila.getTimestamp("invalidada_en"), zona),
                            vacio(fila.getString("motivo_invalidacion"))));
                }, empresaId, desde.getYear() * 100 + desde.getMonthValue(), hasta.getYear() * 100 + hasta.getMonthValue());
        return csv.toString();
    }

    private static String leeme(User actor, YearMonth desde, YearMonth hasta) {
        return String.join("\r\n",
                "Registro de jornada de " + actor.getEmpresa().getNombre() + ", de " + desde + " a " + hasta + ".",
                "Exportado el " + FECHA_HORA.format(Instant.now().atZone(actor.zona())) + " por "
                        + actor.getNombre() + ", para la Inspección de Trabajo o la representación legal",
                "de las personas trabajadoras (art. 34.9 del Estatuto de los Trabajadores).",
                "",
                "horas-AAAA-MM.xlsx   Todas las jornadas cerradas del mes, de toda la plantilla, con sus totales.",
                "registros/           El registro mensual oficial de cada persona, en PDF, con su firma si la hay.",
                "correcciones.csv     Cada alta, cambio, corrección o anulación de un fichaje: quién, cuándo y por qué.",
                "firmas.csv           Las firmas mensuales de la plantilla, vigentes e invalidadas.",
                "",
                "Las horas van en la zona horaria de la empresa (" + actor.zona().getId() + ").",
                "La traza de auditoría está encadenada con huellas: la integridad se puede comprobar en la aplicación.",
                "");
    }

    /** Un nombre de persona como nombre de fichero: sin tildes, espacios ni barras. */
    static String nombreDeFichero(String nombre) {
        String sinTildes = Normalizer.normalize(nombre == null ? "persona" : nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String limpio = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return limpio.isEmpty() ? "persona" : limpio;
    }

    private static void entrada(ZipOutputStream zip, String nombre, byte[] contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido);
        zip.closeEntry();
    }

    private static byte[] conBom(String texto) {
        byte[] cuerpo = texto.getBytes(StandardCharsets.UTF_8);
        byte[] conBom = new byte[cuerpo.length + 3];
        conBom[0] = (byte) 0xEF;
        conBom[1] = (byte) 0xBB;
        conBom[2] = (byte) 0xBF;
        System.arraycopy(cuerpo, 0, conBom, 3, cuerpo.length);
        return conBom;
    }

    private static void linea(StringBuilder csv, List<String> celdas) {
        csv.append(String.join(";", celdas.stream().map(AbsenteeismCsv::celda).toList())).append("\r\n");
    }

    private static String fecha(Timestamp instante, ZoneId zona) {
        return instante == null ? "" : FECHA_HORA.format(instante.toInstant().atZone(zona));
    }

    private static String vacio(String texto) {
        return texto == null ? "" : texto;
    }

    /** El PDF cierra el flujo al terminar; dentro de un ZIP eso cerraría el ZIP entero. */
    private static final class SinCerrar extends FilterOutputStream {
        SinCerrar(OutputStream destino) {
            super(destino);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
