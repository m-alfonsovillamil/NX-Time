package com.nxtime.nxtime.service.impl;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Borra los registros de empresa que nadie llegó a confirmar.
 *
 * Desde la V37 (ADR 034), registrar una empresa la deja pendiente hasta que su
 * ADMIN canjea el código que le llega al correo. Quien no lo canjea nunca --el
 * código no llegó, se equivocó de correo, o estaba probando con uno ajeno-- deja
 * una empresa que nadie puede usar y que <b>ocupa su nombre</b>: el siguiente
 * que quiera registrar «Talleres López» recibe «La empresa ya existe».
 *
 * <p>Solo se borra lo que es seguro borrar, y la base ayuda a que sea así:
 * <ul>
 *   <li>La empresa no tiene a nadie más. Un ADMIN sin confirmar no puede entrar,
 *       así que no ha podido dar de alta a nadie; si hay alguien, no se toca.</li>
 *   <li>Todas las claves hacia {@code usuarios} y {@code empresas} son RESTRICT:
 *       si esa cuenta tuviera cualquier otra cosa (un fichaje, un aviso), el
 *       DELETE falla y ese registro se deja como está.</li>
 * </ul>
 *
 * Cada registro va en su transacción: uno que no se puede borrar no deja a
 * medias a los demás. Va en SQL explícito por lo mismo que
 * {@link PersonalDataEraser}: es código que destruye datos y tiene que poder
 * leerse entero.
 */
@Component
public class UnconfirmedRegistrationCleaner {

    /**
     * Dos días: el código de confirmación caduca mucho antes, y quien vuelva
     * al tercer día registra la empresa otra vez, que es un minuto.
     */
    public static final Duration PLAZO = Duration.ofHours(48);

    private static final Logger log = LoggerFactory.getLogger(UnconfirmedRegistrationCleaner.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public UnconfirmedRegistrationCleaner(JdbcTemplate jdbc, TransactionTemplate transaccion) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
    }

    private record Pendiente(long usuarioId, long empresaId) {
    }

    /** Borra los que llevan sin confirmar desde antes de {@code ahora - PLAZO}. Devuelve cuántos. */
    public int borrarCaducados(Instant ahora) {
        Timestamp limite = Timestamp.from(ahora.minus(PLAZO));
        List<Pendiente> pendientes = jdbc.query("""
                SELECT u.id, u.empresa_id
                  FROM usuarios u
                 WHERE u.correo_sin_confirmar_desde < ?
                   AND NOT EXISTS (SELECT 1 FROM usuarios otro
                                    WHERE otro.empresa_id = u.empresa_id AND otro.id <> u.id)
                """,
                (fila, n) -> new Pendiente(fila.getLong("id"), fila.getLong("empresa_id")), limite);

        int borrados = 0;
        for (Pendiente pendiente : pendientes) {
            try {
                Boolean borrado = transaccion.execute(estado -> borrar(pendiente, limite));
                if (Boolean.TRUE.equals(borrado)) {
                    borrados++;
                }
            } catch (DataIntegrityViolationException e) {
                // Tiene algo que no debería tener una cuenta que nunca entró.
                // No se fuerza: se deja y se avisa, para mirarlo a mano.
                log.warn("El registro sin confirmar del usuario {} (empresa {}) no se ha podido borrar: {}",
                        pendiente.usuarioId(), pendiente.empresaId(), e.getMostSpecificCause().getMessage());
            }
        }
        if (borrados > 0) {
            log.info("Borrados {} registros de empresa sin confirmar tras {} horas.", borrados, PLAZO.toHours());
        }
        return borrados;
    }

    private boolean borrar(Pendiente pendiente, Timestamp limite) {
        jdbc.update("DELETE FROM codigos_acceso WHERE usuario_id = ?", pendiente.usuarioId());
        // Se vuelve a mirar la fecha: entre la consulta y esto puede haber
        // confirmado, o haber vuelto a registrarse (que la pone a ahora).
        int cuentas = jdbc.update("DELETE FROM usuarios WHERE id = ? AND correo_sin_confirmar_desde < ?",
                pendiente.usuarioId(), limite);
        if (cuentas == 0) {
            return false;
        }
        jdbc.update("DELETE FROM empresas WHERE id = ?", pendiente.empresaId());
        return true;
    }
}
