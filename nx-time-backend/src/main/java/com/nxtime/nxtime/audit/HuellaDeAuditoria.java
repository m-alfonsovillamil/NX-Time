package com.nxtime.nxtime.audit;

import com.nxtime.nxtime.domain.TimeEntryAudit;
import java.time.temporal.ChronoUnit;
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
     * La clave del advisory lock con el que se serializa el encadenamiento
     * (ver {@code TimeEntryAuditRepository#bloquearCadena}).
     *
     * Vive aquí y no en el listener porque es parte de lo que define la
     * cadena, igual que la versión del hash: quien encadene una fila tiene que
     * pedir <b>esta</b> clave y no otra, o no se estará serializando con nadie.
     *
     * El valor en sí es arbitrario --solo tiene que ser estable y no chocar con
     * otro advisory lock de la aplicación; hoy es el único que hay-- y está
     * elegido para que se reconozca de un vistazo en {@code pg_locks}.
     */
    public static final long CLAVE_DEL_LOCK_DE_CADENA = 8_2019_0312L;

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
        return Canonico.sha256(payload);
    }

    /** La hora tal y como se guarda: microsegundos, ni uno más. */
    public String marcaDeTiempo(TimeEntryAudit fila) {
        return fila.getFechaHora().truncatedTo(ChronoUnit.MICROS).toString();
    }

    /** Ver {@link Canonico#json}: la forma canónica vive allí desde la Fase B3. */
    String canonizar(String json) {
        return Canonico.json(json);
    }
}
