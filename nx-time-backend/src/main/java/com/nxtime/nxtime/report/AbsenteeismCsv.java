package com.nxtime.nxtime.report;

import com.nxtime.nxtime.dto.AbsenteeismResponse;
import com.nxtime.nxtime.dto.AbsenteeismRow;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * El absentismo de un periodo en CSV (Fase B4), para abrirlo en una hoja de
 * cálculo. Es el primer CSV del proyecto, y tres decisiones van por quien lo
 * abre, que es alguien de RRHH con un Excel en español:
 *
 * <ul>
 *   <li><b>Punto y coma y coma decimal.</b> Un Excel configurado en español
 *       abre un CSV con comas en una sola columna, y lee "4.2" como una fecha
 *       o como cuarenta y dos.</li>
 *   <li><b>BOM de UTF-8 al principio.</b> Sin él, Excel supone Windows-1252 y
 *       "Martínez" sale como "MartÃ­nez".</li>
 *   <li><b>Neutraliza fórmulas.</b> Un nombre lo escribe la propia persona en
 *       su perfil, y uno que empiece por {@code =}, {@code +}, {@code -} o
 *       {@code @} lo ejecuta la hoja de cálculo al abrir el fichero (inyección
 *       de CSV, OWASP). Se le antepone un apóstrofo, que es lo que hace Excel
 *       para decir "esto es texto".</li>
 * </ul>
 *
 * Una fila por grupo, más el total al final. Los motivos van en una sola
 * columna ("Consulta médica: 3; Otros: 1") porque cada periodo tiene los suyos
 * y una columna por motivo haría que dos exportaciones no se pudieran pegar una
 * debajo de otra.
 */
public final class AbsenteeismCsv {

    private static final String SEPARADOR = ";";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final List<String> CABECERA = List.of(
            "Grupo", "Personas", "Días laborables", "Días trabajados", "Días de ausencia con motivo",
            "Días sin fichaje ni ausencia", "Días de vacaciones", "Absentismo (%)",
            "Absentismo sin justificar (%)", "Motivos");

    private AbsenteeismCsv() {
    }

    public static byte[] generar(AbsenteeismResponse absentismo) {
        StringBuilder csv = new StringBuilder();
        linea(csv, CABECERA);
        absentismo.filas().forEach(fila -> linea(csv, celdas(fila)));
        linea(csv, celdas(absentismo.total()));

        byte[] texto = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] conBom = new byte[BOM.length + texto.length];
        System.arraycopy(BOM, 0, conBom, 0, BOM.length);
        System.arraycopy(texto, 0, conBom, BOM.length, texto.length);
        return conBom;
    }

    private static List<String> celdas(AbsenteeismRow fila) {
        List<String> celdas = new ArrayList<>();
        celdas.add(fila.nombre());
        celdas.add(String.valueOf(fila.personas()));
        celdas.add(String.valueOf(fila.diasLaborables()));
        celdas.add(String.valueOf(fila.diasTrabajados()));
        celdas.add(String.valueOf(fila.diasAusenciaJustificada()));
        celdas.add(String.valueOf(fila.diasSinFichaje()));
        celdas.add(String.valueOf(fila.diasVacaciones()));
        celdas.add(decimal(fila.absentismo()));
        celdas.add(decimal(fila.absentismoSinJustificar()));
        celdas.add(String.join("; ", fila.motivos().stream()
                .map(motivo -> motivo.etiqueta() + ": " + motivo.dias())
                .toList()));
        return celdas;
    }

    /** Vacío si no hay porcentaje: una celda en blanco no se confunde con un 0 %. */
    private static String decimal(BigDecimal valor) {
        return valor == null ? "" : valor.toPlainString().replace('.', ',');
    }

    private static void linea(StringBuilder csv, List<String> celdas) {
        csv.append(String.join(SEPARADOR, celdas.stream().map(AbsenteeismCsv::celda).toList()));
        // CRLF: es lo que pide la RFC 4180 y lo que Excel escribe.
        csv.append("\r\n");
    }

    static String celda(String valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor;
        if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.contains(SEPARADOR) || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            texto = "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
