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
import org.springframework.transaction.TransactionDefinition;
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

    /** Para {@link #descartar}: su propia transacción, que no se deshaga con la de quien llama. */
    private final TransactionTemplate aparte;

    public UnconfirmedRegistrationCleaner(JdbcTemplate jdbc, TransactionTemplate transaccion) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.aparte = new TransactionTemplate(transaccion.getTransactionManager());
        this.aparte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

    /**
     * Borra ya el registro sin confirmar de esa cuenta, sin esperar al plazo
     * (revisión de seguridad del 17/10/2026, plan J).
     *
     * Es para cuando el dueño del correo aparece por otro camino --entra o se
     * registra con Google o Microsoft, que garantizan que el correo es suyo--
     * y lo que hay a medias con su correo lo puede haber dejado otro, con una
     * contraseña que el dueño no conoce. Darlo por confirmado, que es lo que se
     * hacía hasta entonces, le regalaba la empresa a quien lo dejó.
     *
     * Va en su propia transacción: quien llama suele lanzar después («no
     * tienes cuenta») y el borrado tiene que quedarse. Las mismas condiciones
     * que el borrado por plazo: la cuenta sigue sin confirmar y es la única de
     * su empresa; si no, o si tiene algo que no debería tener, no se toca.
     *
     * @return si lo ha borrado
     */
    public boolean descartar(long usuarioId) {
        try {
            return Boolean.TRUE.equals(aparte.execute(estado -> {
                List<Pendiente> pendiente = jdbc.query("""
                        SELECT u.id, u.empresa_id
                          FROM usuarios u
                         WHERE u.id = ?
                           AND u.correo_sin_confirmar_desde IS NOT NULL
                           AND NOT EXISTS (SELECT 1 FROM usuarios otro
                                            WHERE otro.empresa_id = u.empresa_id AND otro.id <> u.id)
                        """,
                        (fila, n) -> new Pendiente(fila.getLong("id"), fila.getLong("empresa_id")), usuarioId);
                // Sin plazo: cualquier fecha de «sin confirmar desde» vale.
                return !pendiente.isEmpty() && borrar(pendiente.getFirst(), Timestamp.valueOf("9999-12-31 00:00:00"));
            }));
        } catch (DataIntegrityViolationException e) {
            log.warn("El registro sin confirmar del usuario {} no se ha podido descartar: {}",
                    usuarioId, e.getMostSpecificCause().getMessage());
            return false;
        }
    }

    /**
     * Borra una empresa que el operador ha rechazado (ADR 046), en la
     * transacción de quien llama.
     *
     * Solo una PENDIENTE: nadie de ella ha llegado a entrar, así que no tiene
     * más que la cuenta de quien la registró y lo que dejó al registrarse (el
     * código del correo, la cuenta de Google o Microsoft con la que lo hizo).
     * Si tuviera cualquier otra cosa, las claves RESTRICT lo impiden y salta
     * {@link DataIntegrityViolationException}: entonces no se fuerza.
     *
     * @return si la ha borrado (no, si ya no estaba pendiente)
     */
    public boolean borrarEmpresaRechazada(long empresaId) {
        Integer pendiente = jdbc.queryForObject(
                "SELECT count(*) FROM empresas WHERE id = ? AND estado = 'PENDIENTE'", Integer.class, empresaId);
        if (pendiente == null || pendiente == 0) {
            return false;
        }
        String deSuGente = "usuario_id IN (SELECT id FROM usuarios WHERE empresa_id = ?)";
        jdbc.update("DELETE FROM identidades_externas WHERE " + deSuGente, empresaId);
        jdbc.update("DELETE FROM codigos_acceso WHERE " + deSuGente, empresaId);
        jdbc.update("DELETE FROM refresh_tokens WHERE " + deSuGente, empresaId);
        jdbc.update("UPDATE empresas SET condiciones_aceptadas_por = NULL WHERE id = ?", empresaId);
        jdbc.update("DELETE FROM usuarios WHERE empresa_id = ?", empresaId);
        jdbc.update("DELETE FROM empresas WHERE id = ? AND estado = 'PENDIENTE'", empresaId);
        log.info("Empresa {} rechazada y borrada.", empresaId);
        return true;
    }

    private boolean borrar(Pendiente pendiente, Timestamp limite) {
        jdbc.update("DELETE FROM codigos_acceso WHERE usuario_id = ?", pendiente.usuarioId());
        // Desde la V43 la empresa apunta a quien aceptó sus condiciones, que
        // es esta misma cuenta (ON DELETE SET NULL lo haría solo; así se lee).
        jdbc.update("UPDATE empresas SET condiciones_aceptadas_por = NULL WHERE id = ?", pendiente.empresaId());
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
