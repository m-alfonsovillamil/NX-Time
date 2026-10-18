package com.nxtime.nxtime.audit;

import com.nxtime.nxtime.domain.AuditAction;
import java.time.Instant;

/**
 * Lo que hace falta de un movimiento de auditoría para comprobar la cadena, y
 * nada más: lo que entra en su hash, el enlace con el anterior y de qué empresa
 * es.
 *
 * Existe para que recorrer la traza no cargue entidades. Con
 * {@code TimeEntryAudit}, cada movimiento traía detrás su fichaje, la persona
 * del fichaje, su empresa y su departamento, de uno en uno: comprobar millón y
 * medio de movimientos eran más de setecientas mil consultas y 46 minutos
 * (docs/PRUEBA-DE-CARGA.md). Con esto es una consulta por bloque.
 *
 * @param modificadoPorId null en las acciones automáticas, sin autor humano
 * @param empresaId       la del fichaje del movimiento
 */
public record FilaDeLaCadena(
        long id,
        long registroId,
        long usuarioId,
        Long modificadoPorId,
        AuditAction accion,
        String valorAnterior,
        String valorNuevo,
        String motivo,
        Instant fechaHora,
        String hashAnterior,
        String hash,
        short versionHash,
        Long empresaId,
        /**
         * Es una lápida (V46, ADR 050): una fila del tramo común de una empresa
         * ya borrada. Solo tiene su sitio y sus hashes, así que de ella se
         * comprueba el enlace, como de las de la versión 1.
         */
        boolean retirada) {

    /** La que sale de la tabla: es la que construye la consulta del repositorio. */
    public FilaDeLaCadena(long id, long registroId, long usuarioId, Long modificadoPorId, AuditAction accion,
            String valorAnterior, String valorNuevo, String motivo, Instant fechaHora, String hashAnterior,
            String hash, short versionHash, Long empresaId) {
        this(id, registroId, usuarioId, modificadoPorId, accion, valorAnterior, valorNuevo, motivo, fechaHora,
                hashAnterior, hash, versionHash, empresaId, false);
    }

    /** Una lápida del tramo común, puesta en el sitio de la fila que fue. */
    public static FilaDeLaCadena lapida(long id, String hashAnterior, String hash, long empresaId) {
        return new FilaDeLaCadena(id, 0L, 0L, null, null, null, null, null, null, hashAnterior, hash, (short) 0,
                empresaId, true);
    }
}
