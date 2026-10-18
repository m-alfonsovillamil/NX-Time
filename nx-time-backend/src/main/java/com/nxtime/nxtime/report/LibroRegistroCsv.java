package com.nxtime.nxtime.report;

import com.nxtime.nxtime.domain.Complaint;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * El libro-registro de las informaciones recibidas (Ley 2/2023, art. 26; ADR
 * 048), en CSV para una hoja de cálculo, con las mismas reglas que
 * {@link AbsenteeismCsv}: punto y coma, UTF-8 con BOM y celdas a salvo de
 * fórmulas.
 *
 * Lleva lo que pide la Autoridad (Recomendación 1/2026): fecha de recepción,
 * objeto, estado y fecha de fin, y además los demás hitos del expediente.
 * <b>No lleva ni la descripción, ni la conclusión, ni quién denunció</b>: es
 * el registro de que el canal funciona, y puede tener que enseñarse a una
 * autoridad. Lo que se contó está en cada expediente, con acceso restringido.
 */
public final class LibroRegistroCsv {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<String> CABECERA = List.of(
            "Número", "Recibida", "Objeto", "Anónima", "Estado", "Acuse de recibo", "Investigación iniciada",
            "Plazo ampliado", "Fin", "Contenido suprimido");

    private LibroRegistroCsv() {
    }

    public static byte[] generar(List<Complaint> denuncias, ZoneId zona) {
        StringBuilder csv = new StringBuilder();
        linea(csv, CABECERA);
        for (Complaint d : denuncias) {
            linea(csv, List.of(
                    String.valueOf(d.getId()),
                    fecha(d.getCreadoEn(), zona),
                    d.getCategoria().getEtiqueta(),
                    d.esAnonima() ? "Sí" : "No",
                    d.getEstado().name(),
                    fecha(d.getAcuseReciboEn(), zona),
                    fecha(d.getInvestigacionIniciadaEn(), zona),
                    fecha(d.getRespuestaProrrogadaEn(), zona),
                    fecha(d.getResueltaEn(), zona),
                    fecha(d.getContenidoSuprimidoEn(), zona)));
        }
        byte[] texto = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] conBom = new byte[BOM.length + texto.length];
        System.arraycopy(BOM, 0, conBom, 0, BOM.length);
        System.arraycopy(texto, 0, conBom, BOM.length, texto.length);
        return conBom;
    }

    private static String fecha(Instant instante, ZoneId zona) {
        return instante == null ? "" : FECHA.format(instante.atZone(zona));
    }

    private static void linea(StringBuilder csv, List<String> celdas) {
        csv.append(String.join(";", celdas.stream().map(AbsenteeismCsv::celda).toList()));
        csv.append("\r\n");
    }
}
