package com.nxtime.nxtime.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
 */
public final class Canonico {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            // Los objetos (no los mapas) también, por nombre de propiedad: un
            // record que reordene sus componentes no puede cambiar la huella.
            .configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

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
        } catch (JsonProcessingException noEsJson) {
            return json;
        }
    }

    /** Un objeto en forma canónica: sus propiedades en orden alfabético, las fechas en ISO. */
    public static String deObjeto(Object objeto) {
        try {
            return json(MAPPER.writeValueAsString(objeto));
        } catch (JsonProcessingException e) {
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
