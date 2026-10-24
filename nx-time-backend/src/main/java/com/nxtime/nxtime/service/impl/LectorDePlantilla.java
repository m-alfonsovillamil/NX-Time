package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;

/**
 * Lee el fichero de una plantilla para importarla (ADR 052): un CSV o un Excel
 * (.xlsx), con una fila de cabecera y una persona por fila.
 *
 * Lo que hace que valga «tal cual sale de Excel»:
 * <ul>
 *   <li>El CSV puede venir en UTF-8 (con o sin BOM) o en Windows-1252, que es
 *       como lo guarda Excel en español con «CSV (delimitado por comas)». Se
 *       prueba UTF-8 estricto y, si no lo es, Windows-1252: así las tildes y
 *       las eñes llegan bien en los dos casos.</li>
 *   <li>El separador se deduce de la cabecera: el Excel en español separa con
 *       punto y coma, otros con coma, y algunos con tabulador.</li>
 *   <li>Las cabeceras se reconocen sin mayúsculas ni tildes, y con los nombres
 *       que se le ocurrirían a cualquiera («correo», «email», «jornada»...).</li>
 * </ul>
 *
 * No valida nada de lo que dice cada fila: eso es cosa de quien importa, que
 * sabe qué departamentos hay y qué puede hacer quien sube el fichero.
 */
final class LectorDePlantilla {

    /** Una plantilla de más se trocea: cada fila es un alta, y cada alta, un correo. */
    static final int MAXIMO_DE_FILAS = 500;

    static final String NOMBRE = "nombre";
    static final String APELLIDOS = "apellidos";
    static final String EMAIL = "email";
    static final String ROL = "rol";
    static final String DEPARTAMENTO = "departamento";
    static final String HORAS = "horas";
    static final String VACACIONES = "vacaciones";

    /** Cómo puede llamarse cada columna, ya sin tildes ni mayúsculas. */
    private static final Map<String, String> SINONIMOS = Map.ofEntries(
            Map.entry("nombre", NOMBRE),
            Map.entry("apellidos", APELLIDOS),
            Map.entry("apellido", APELLIDOS),
            Map.entry("email", EMAIL),
            Map.entry("e-mail", EMAIL),
            Map.entry("correo", EMAIL),
            Map.entry("correo electronico", EMAIL),
            Map.entry("rol", ROL),
            Map.entry("perfil", ROL),
            Map.entry("departamento", DEPARTAMENTO),
            Map.entry("dpto", DEPARTAMENTO),
            Map.entry("horas", HORAS),
            Map.entry("horas semanales", HORAS),
            Map.entry("jornada", HORAS),
            Map.entry("jornada semanal", HORAS),
            Map.entry("vacaciones", VACACIONES),
            Map.entry("dias de vacaciones", VACACIONES),
            Map.entry("dias vacaciones", VACACIONES));

    /** Una fila leída: su número en el fichero (la cabecera es la 1) y sus valores por columna. */
    record Fila(int numero, Map<String, String> valores) {
        String valor(String columna) {
            String valor = valores.get(columna);
            return valor == null ? "" : valor.strip();
        }
    }

    /** Lo leído: las filas, y las columnas que no se han reconocido (se ignoran). */
    record Leido(List<Fila> filas, List<String> columnasIgnoradas) {
    }

    private LectorDePlantilla() {
    }

    static Leido leer(String nombreDelFichero, byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw invalido("El fichero está vacío.");
        }
        String nombre = nombreDelFichero == null ? "" : nombreDelFichero.toLowerCase(Locale.ROOT);
        List<List<String>> tabla = nombre.endsWith(".xlsx") ? deExcel(contenido) : deCsv(contenido);
        tabla.removeIf(LectorDePlantilla::vacia);
        if (tabla.isEmpty()) {
            throw invalido("El fichero está vacío.");
        }

        List<String> cabecera = tabla.get(0);
        List<String> columnas = new ArrayList<>();
        List<String> ignoradas = new ArrayList<>();
        for (String titulo : cabecera) {
            String columna = SINONIMOS.get(normalizar(titulo));
            columnas.add(columna);
            if (columna == null && !titulo.isBlank()) {
                ignoradas.add(titulo.strip());
            }
        }
        for (String obligatoria : List.of(NOMBRE, APELLIDOS, EMAIL)) {
            if (!columnas.contains(obligatoria)) {
                throw invalido("Falta la columna «" + obligatoria + "». La primera fila tiene que llevar los títulos: "
                        + "nombre, apellidos y email, y si quieres rol, departamento, horas y vacaciones.");
            }
        }
        if (tabla.size() - 1 > MAXIMO_DE_FILAS) {
            throw invalido("Son " + (tabla.size() - 1) + " personas, y como mucho caben " + MAXIMO_DE_FILAS
                    + " por fichero. Pártelo en varios.");
        }

        List<Fila> filas = new ArrayList<>();
        for (int i = 1; i < tabla.size(); i++) {
            List<String> celdas = tabla.get(i);
            Map<String, String> valores = new LinkedHashMap<>();
            for (int c = 0; c < columnas.size() && c < celdas.size(); c++) {
                if (columnas.get(c) != null) {
                    valores.put(columnas.get(c), celdas.get(c));
                }
            }
            filas.add(new Fila(i + 1, valores));
        }
        if (filas.isEmpty()) {
            throw invalido("El fichero solo tiene la fila de títulos: no hay nadie que dar de alta.");
        }
        return new Leido(filas, ignoradas);
    }

    /** Sin tildes, en minúsculas, con un espacio entre palabras: «Correo  Electrónico» → «correo electronico». */
    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("\\s+", " ").strip();
    }

    private static boolean vacia(List<String> fila) {
        return fila.stream().allMatch(String::isBlank);
    }

    private static BusinessException invalido(String motivo) {
        return new BusinessException(motivo, HttpStatus.BAD_REQUEST);
    }

    // ---- Excel ----

    private static List<List<String>> deExcel(byte[] contenido) {
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet hoja = libro.getSheetAt(0);
            if (hoja.getLastRowNum() > MAXIMO_DE_FILAS + 50) {
                throw invalido("El Excel tiene " + hoja.getLastRowNum() + " filas, y como mucho caben "
                        + MAXIMO_DE_FILAS + " personas por fichero. Pártelo en varios.");
            }
            DataFormatter formato = new DataFormatter(Locale.of("es", "ES"));
            List<List<String>> tabla = new ArrayList<>();
            for (int r = 0; r <= hoja.getLastRowNum(); r++) {
                Row fila = hoja.getRow(r);
                List<String> celdas = new ArrayList<>();
                if (fila != null) {
                    for (int c = 0; c < fila.getLastCellNum(); c++) {
                        Cell celda = fila.getCell(c);
                        celdas.add(celda == null ? "" : formato.formatCellValue(celda));
                    }
                }
                tabla.add(celdas);
            }
            return tabla;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalido("No se ha podido leer el Excel. Guárdalo como .xlsx, o como CSV.");
        }
    }

    // ---- CSV ----

    private static List<List<String>> deCsv(byte[] contenido) {
        String texto = decodificar(contenido);
        if (texto.startsWith("﻿")) {
            texto = texto.substring(1);
        }
        int finDeCabecera = texto.indexOf('\n');
        String cabecera = finDeCabecera < 0 ? texto : texto.substring(0, finDeCabecera);
        char separador = separadorDe(cabecera);
        return trocear(texto, separador);
    }

    private static String decodificar(byte[] contenido) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenido))
                    .toString();
        } catch (CharacterCodingException noEsUtf8) {
            return new String(contenido, Charset.forName("windows-1252"));
        }
    }

    private static char separadorDe(String cabecera) {
        long puntosYComa = cabecera.chars().filter(c -> c == ';').count();
        long comas = cabecera.chars().filter(c -> c == ',').count();
        long tabuladores = cabecera.chars().filter(c -> c == '\t').count();
        if (tabuladores > puntosYComa && tabuladores > comas) {
            return '\t';
        }
        return puntosYComa >= comas && puntosYComa > 0 ? ';' : ',';
    }

    /** CSV de la RFC 4180: comillas dobles para lo que lleva separadores o saltos, y "" para una comilla. */
    static List<List<String>> trocear(String texto, char separador) {
        List<List<String>> filas = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        celda.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    celda.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == separador) {
                fila.add(celda.toString());
                celda.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                fila.add(celda.toString());
                celda.setLength(0);
                filas.add(fila);
                fila = new ArrayList<>();
            } else {
                celda.append(c);
            }
        }
        if (celda.length() > 0 || !fila.isEmpty()) {
            fila.add(celda.toString());
            filas.add(fila);
        }
        return filas;
    }
}
