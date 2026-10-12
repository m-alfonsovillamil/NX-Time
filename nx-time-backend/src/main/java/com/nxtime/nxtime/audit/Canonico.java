package com.nxtime.nxtime.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * La forma canónica de lo que se firma, y su SHA-256: una sola definición para
 * la cadena de auditoría ({@link HuellaDeAuditoria}) y para la firma mensual
 * ({@link HuellaDelMes}).
 *
 * Salió de HuellaDeAuditoria en la Fase B3, y lo único que no podía hacer al
 * salir es cambiar un hash: la auditoría histórica se escribió con esta
 * definición, y el verificador recalcula con la de hoy. HuellaDeAuditoriaTest
 * lo fija con dos valores calculados antes de moverla.
 *
 * <b>Canónico</b> quiere decir: claves ordenadas y sin espacios, con un mapper
 * propio. Lo que se firma no puede depender de cómo esté configurado el de
 * Spring hoy; si alguien le cambia una opción al de la aplicación, los hashes
 * de mañana dejarían de cuadrar con los de ayer sin que nadie lo note.
 *
 * Tampoco puede depender de la versión de Jackson. Desde octubre de 2026 es la
 * 3; {@code CanonicoFrenteAJackson2Test} guarda la definición de la 2 y
 * compara las dos texto a texto. Subir de versión mayor pide repetir eso.
 */
public final class Canonico {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            // Los objetos (no los mapas) también, por nombre de propiedad: un
            // record que reordene sus componentes no puede cambiar la huella.
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Lo único en que Jackson 3 se separaba de Jackson 2, con el que se
            // firmó hasta octubre de 2026: por defecto rechaza lo que sobre
            // detrás de un JSON completo, y el 2 lo ignoraba. De una columna
            // jsonb no puede venir, pero la definición tiene que ser la misma
            // (ver CanonicoFrenteAJackson2Test y el ADR 043).
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private Canonico() {
    }

    /**
     * El mismo JSON escrito siempre igual: claves ordenadas y sin espacios.
     *
     * Da lo mismo si viene del texto que generó Jackson o de lo que devuelve
     * {@code jsonb} tras reescribirlo: las dos formas producen esto. Lo que no
     * sea JSON válido se devuelve tal cual --no debería pasar, pero una fila
     * rara no puede tumbar la escritura de la auditoría.
     */
    public static String json(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(MAPPER.readValue(json, Object.class));
        } catch (JacksonException noEsJson) {
            return json;
        }
    }

    /** Un objeto en forma canónica: sus propiedades en orden alfabético, las fechas en ISO. */
    public static String deObjeto(Object objeto) {
        try {
            return json(MAPPER.writeValueAsString(objeto));
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo serializar para firmar", e);
        }
    }

    /** SHA-256 en hexadecimal, sobre los bytes UTF-8. */
    public static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 está garantizado en cualquier JVM estándar; el
            // compilador exige capturar la excepción comprobada.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
