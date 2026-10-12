package com.nxtime.nxtime.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link Canonico} escribe con Jackson 3 exactamente lo que escribía con
 * Jackson 2 (ADR 043).
 *
 * La auditoría de antes de octubre de 2026 se firmó con la forma canónica que
 * daba Jackson 2, y el verificador recalcula con la de hoy: si las dos
 * difieren en un carácter para alguna fila, esa fila pasa a «no cuadra» sin
 * que nadie la haya tocado. {@link HuellaDeAuditoriaTest} fija dos hashes;
 * esto compara las dos definiciones enteras, texto a texto, sobre todo lo que
 * una versión podría escribir distinto de la otra.
 *
 * La definición vieja está copiada aquí tal cual estaba en {@code Canonico}, y
 * Jackson 2 es dependencia solo de los tests para poder ejecutarla.
 */
class CanonicoFrenteAJackson2Test {

    private static final ObjectMapper EL_DE_ANTES = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private static String jsonDeAntes(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            return EL_DE_ANTES.writeValueAsString(EL_DE_ANTES.readValue(json, Object.class));
        } catch (JsonProcessingException noEsJson) {
            return json;
        }
    }

    private static String deObjetoDeAntes(Object objeto) throws JsonProcessingException {
        return jsonDeAntes(EL_DE_ANTES.writeValueAsString(objeto));
    }

    static Stream<String> textos() {
        List<String> textos = new ArrayList<>(List.of(
                // Lo que se guarda de verdad: la instantánea de un fichaje, como
                // la escribe el serializador y como la devuelve jsonb.
                "{\"id\":42,\"horaEntrada\":\"2026-08-14T07:00:00Z\",\"horaSalida\":null,\"enPausa\":false,"
                        + "\"inicioPausaActual\":null,\"segundosPausaAcumulados\":0,\"anulado\":false,"
                        + "\"jornadaIncompleta\":false}",
                "{\"id\": 42, \"anulado\": false, \"enPausa\": true, \"horaSalida\": null, "
                        + "\"horaEntrada\": \"2026-08-14T07:00:00.123456Z\", \"jornadaIncompleta\": true, "
                        + "\"inicioPausaActual\": \"2026-08-14T10:15:00.000001Z\", \"segundosPausaAcumulados\": 5400}",
                // Fechas como número, por si alguna fila antigua se escribió así.
                "{\"horaEntrada\": 1757926800.123456000, \"horaSalida\": 1757955600, \"id\": 9007199254740993}",
                // Números.
                "[0, -0, 1, -1, 2147483647, 2147483648, 9223372036854775807, 9223372036854775808, "
                        + "123456789012345678901234567890]",
                "[0.0, -0.0, 1.0, 1.10, 100.00, 0.1, 0.30000000000000004, 1e10, 1E10, 1e-7, 1.5e300, 1e400, "
                        + "-1e400, 4.9e-324, 2.2250738585072014E-308, 1757926800.123456789, 3.141592653589793238462643]",
                // Textos.
                "{\"barra\":\"a/b\",\"escapada\":\"a\\/b\",\"zona\":\"Europe/Madrid\",\"url\":\"https://nxtime-web.com/x?y=1&z=<2>\"}",
                "{\"comillas\":\"dijo \\\"hola\\\"\",\"contrabarra\":\"C:\\\\ruta\",\"saltos\":\"uno\\ndos\\ttres\\r\"}",
                "{\"tildes\":\"Olvidé fichar la salida, ¿vale? ñ ü ç €\",\"escapadas\":\"\\u00e9\\u00f1\\u20ac\"}",
                "{\"emoji\":\"😀 👍🏽\",\"escapado\":\"\\ud83d\\ude00\",\"suelto\":\"\\ud83d\"}",
                "{\"control\":\"\\u0000\\u0001\\u001f\\u007f\\u0080\\u2028\\u2029\\ufeff\",\"vacio\":\"\"}",
                "{\"ángulos\":\"<script>alert('x')</script>\",\"ampersand\":\"a&b\",\"apóstrofo\":\"it's\"}",
                // Formas.
                "{}", "[]", "[[]]", "{\"a\":{}}", "{\"a\":[]}", "null", "true", "false", "0", "\"texto\"", "\"\"",
                "{\"b\":1,\"a\":2,\"B\":3,\"A\":4,\"á\":5,\"_\":6,\"1\":7,\"10\":8,\"2\":9,\"\":10}",
                "{\"a\":1,\"a\":2}",
                "{\"anidado\":{\"z\":[{\"y\":1,\"x\":[true,false,null]},{\"b\":{\"d\":1,\"c\":2}}],\"a\":null}}",
                "  {\"espacios\"  :  1  ,\n\t\"y\" : [ 1 , 2 ]\n}  ",
                // Lo que no es JSON, o lo es a medias: se devuelve tal cual o se
                // lee hasta donde se pueda, pero igual que antes.
                "{\"a\":1} sobra", "{\"a\":1}{\"b\":2}", "[1,2] [3]", "12 13", "{\"a\":1}\n", "{\"a\":1}//fin",
                "{a:1}", "{'a':1}", "{\"a\":1,}", "[1,]", "[,1]", "{\"a\"}", "{\"a\":}", "{", "[", "\"sin cerrar",
                "NaN", "Infinity", "-Infinity", "01", "+1", "1.", ".5", "0x10", "1e", "tru", "nul", "TRUE",
                "/* comentario */ {\"a\":1}", "// comentario\n{\"a\":1}", "{\"a\":\"salto\ncrudo\"}",
                "{\"a\":\"tab\tcrudo\"}", "texto suelto", "<xml/>", "\uFEFF{\"a\":1}", "\u0000", "{\"a\":1}\u0000",
                "[" .repeat(600) + "]".repeat(600),
                "\"" + "x".repeat(70_000) + "\"",
                "\"" + "é/".repeat(40_000) + "\""));

        // Y unos cuantos números al azar, con semilla fija: los decimales son
        // donde dos versiones de una librería más se pueden separar.
        Random azar = new Random(20261012L);
        for (int i = 0; i < 400; i++) {
            StringBuilder lista = new StringBuilder("[");
            for (int j = 0; j < 25; j++) {
                if (j > 0) {
                    lista.append(',');
                }
                switch (azar.nextInt(4)) {
                    case 0 -> lista.append(azar.nextLong());
                    case 1 -> lista.append(azar.nextDouble() * Math.pow(10, azar.nextInt(40) - 20));
                    // Cualquier double, también NaN o Infinity: entonces la
                    // lista entera no es JSON, y eso también tiene que dar igual.
                    case 2 -> lista.append(Double.longBitsToDouble(azar.nextLong()));
                    default -> lista.append(azar.nextInt(2_000_000_000)).append('.')
                            .append(String.format("%09d", azar.nextInt(1_000_000_000)));
                }
            }
            textos.add(lista.append(']').toString());
        }
        return textos.stream();
    }

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("textos")
    @DisplayName("El mismo JSON da la misma forma canónica que con Jackson 2")
    void elMismoTexto(String json) {
        assertThat(Canonico.json(json)).isEqualTo(jsonDeAntes(json));
    }

    @Test
    @DisplayName("Lo vacío y lo nulo siguen siendo la cadena vacía")
    void loVacio() {
        assertThat(Canonico.json(null)).isEqualTo(jsonDeAntes(null)).isEmpty();
        assertThat(Canonico.json("   ")).isEqualTo(jsonDeAntes("   ")).isEmpty();
    }

    @Test
    @DisplayName("El resumen de un mes se escribe igual: es lo que firma quien firma su registro")
    void elResumenDelMes() throws Exception {
        List<HuellaDelMes.Jornada> jornadas = List.of(
                new HuellaDelMes.Jornada(Instant.parse("2026-09-01T07:00:00Z"), Instant.parse("2026-09-01T15:00:00Z"), 1800, false),
                new HuellaDelMes.Jornada(Instant.parse("2026-09-02T06:59:59Z"), null, 0, false),
                new HuellaDelMes.Jornada(Instant.parse("2026-09-03T07:00:00Z"), Instant.parse("2026-09-03T21:59:59Z"), 0, true),
                // Con fracciones, que el resumen de verdad trunca: aquí importa
                // que las dos versiones las escriban igual si alguna vez llegan.
                new HuellaDelMes.Jornada(Instant.parse("2026-09-04T07:00:00.5Z"),
                        Instant.parse("2026-09-04T15:00:00.123456789Z"), 60, false),
                new HuellaDelMes.Jornada(Instant.EPOCH, Instant.parse("1999-12-31T23:59:59.999999Z"), Long.MAX_VALUE, true));
        for (HuellaDelMes.Resumen resumen : List.of(
                new HuellaDelMes.Resumen(HuellaDelMes.VERSION, 7, 3, "2026-09", jornadas.size(), 86_400, jornadas),
                new HuellaDelMes.Resumen((short) 1, Long.MAX_VALUE, 1, "2026-02", 0, 0, List.of()))) {
            assertThat(Canonico.deObjeto(resumen)).isEqualTo(deObjetoDeAntes(resumen));
        }
    }
}
