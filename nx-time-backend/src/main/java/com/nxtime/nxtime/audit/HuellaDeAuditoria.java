package com.nxtime.nxtime.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * El SHA-256 de una fila de auditoría: una sola definición, para escribirla y
 * para comprobarla después.
 *
 * <h2>Por qué existe esta clase</h2>
 *
 * El encadenamiento de hashes se escribía desde agosto de 2026, pero **no se
 * podía verificar**, y no por falta de ganas: los datos que hacían falta para
 * recalcular el hash se perdían al guardarlos, por dos motivos independientes
 * que se confirmaron ejecutando (ninguna de las 18 filas de una base real se
 * pudo recalcular, ni una):
 *
 * <ol>
 *   <li><b>La marca de tiempo.</b> {@code Instant.now()} tiene precisión de
 *       nanosegundos y la columna {@code TIMESTAMPTZ} guarda microsegundos.
 *       Los nanos entraban en el hash y luego desaparecían.</li>
 *   <li><b>El JSON.</b> Las columnas son {@code jsonb}, que **no conserva el
 *       texto original**: reordena las claves y añade un espacio tras los dos
 *       puntos. Releerlo devolvía una cadena distinta de la que se firmó.</li>
 * </ol>
 *
 * Una cadena que nadie puede comprobar no demuestra nada: detecta que faltan o
 * sobran filas, pero no que a una fila le hayan cambiado el contenido, que es
 * justo lo que la normativa pide poder demostrar.
 *
 * <h2>Cómo se arregla</h2>
 *
 * Las dos causas se quitan en el origen:
 *
 * <ul>
 *   <li>la hora se trunca a microsegundos <b>antes</b> de firmarla, así que lo
 *       que se guarda es exactamente lo que se firmó;</li>
 *   <li>el JSON se pasa a una forma <b>canónica</b> --claves ordenadas y sin
 *       espacios-- antes de entrar en el hash. Esa forma se obtiene igual
 *       desde el texto original que desde lo que devuelve {@code jsonb}, que
 *       es lo que hace posible comprobarlo.</li>
 * </ul>
 *
 * Las filas escritas antes de esto ({@code versionHash} 1) **no se pueden
 * comprobar recalculando**, y no hay forma de arreglarlo hacia atrás: el dato
 * se perdió. De esas solo se puede verificar el enlace con la anterior, y el
 * verificador lo dice en vez de dar por buenas unas y por rotas otras.
 */
@Component
public class HuellaDeAuditoria {

    /** Filas cuyo hash se puede recalcular. Las de antes son la versión 1. */
    public static final short VERSION_VERIFICABLE = 2;

    /**
     * Un mapper aparte del de la aplicación, y con las claves ordenadas: lo
     * que se firma no puede depender de cómo esté configurado el de Spring
     * hoy. Si alguien le cambia una opción al de la aplicación, los hashes de
     * mañana dejarían de cuadrar con los de ayer sin que nadie lo note.
     */
    private final ObjectMapper canonico = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /**
     * El hash de una fila, encadenado al de la anterior.
     *
     * Se le pasa el hash anterior aparte y no se lee de la fila a propósito:
     * al escribir todavía no está puesto, y al verificar interesa comprobar
     * contra el de la fila de al lado, no contra el que la fila dice tener.
     */
    public String calcular(TimeEntryAudit fila, String hashAnterior) {
        String payload = String.join("|",
                String.valueOf(fila.getRegistro().getId()),
                String.valueOf(fila.getUsuario().getId()),
                // "sistema" para las acciones automáticas sin autor humano.
                (fila.getModificadoPor() != null) ? String.valueOf(fila.getModificadoPor().getId()) : "sistema",
                fila.getAccion().name(),
                canonizar(fila.getValorAnterior()),
                canonizar(fila.getValorNuevo()),
                Objects.toString(fila.getMotivo(), ""),
                marcaDeTiempo(fila),
                Objects.toString(hashAnterior, ""));
        return sha256(payload);
    }

    /** La hora tal y como se guarda: microsegundos, ni uno más. */
    public String marcaDeTiempo(TimeEntryAudit fila) {
        return fila.getFechaHora().truncatedTo(ChronoUnit.MICROS).toString();
    }

    /**
     * El mismo JSON escrito siempre igual: claves ordenadas y sin espacios.
     *
     * Da lo mismo si viene del texto que generó Jackson o de lo que devuelve
     * {@code jsonb} tras reescribirlo: las dos formas producen esto. Lo que no
     * sea JSON válido se firma tal cual --no debería pasar, pero una fila rara
     * no puede tumbar la escritura de la auditoría.
     */
    String canonizar(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            return canonico.writeValueAsString(canonico.readValue(json, Object.class));
        } catch (JsonProcessingException noEsJson) {
            return json;
        }
    }

    private String sha256(String payload) {
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
