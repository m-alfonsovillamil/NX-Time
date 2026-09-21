package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.audit.VerificadorDeAuditoria;
import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.service.TaskMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Comprueba cada noche que la cadena de auditoría sigue intacta (Fase A4).
 *
 * <b>Por qué existe.</b> La verificación ya se podía pedir desde
 * {@code GET /api/v1/auditoria/integridad}, pero solo la pedía quien se
 * acordaba — y costaba cara, así que casi nadie. Una integridad que solo se
 * comprueba cuando alguien sospecha llega tarde por definición: lo que la
 * normativa pide demostrar es que el registro no se ha tocado, y eso se
 * demuestra mirando a menudo, no mirando bien una vez.
 *
 * Es incremental: arranca desde el último punto de control y solo revisa lo
 * escrito desde entonces (ver {@link VerificadorDeAuditoria}). La noche que no
 * haya habido fichajes no hay nada que revisar, y también queda registrado —
 * una tarea que no deja rastro no se distingue de una que no corrió.
 *
 * <b>Qué pasa si encuentra algo.</b> No se anota punto de control, se registra
 * la ejecución como error con la fila y el motivo, y eso llega a Sentry y a
 * {@code /estado/tareas}. No se intenta arreglar nada: la tabla es append-only
 * a propósito, y una cadena rota es un hecho que hay que investigar, no una
 * incidencia que cerrar.
 */
@Component
public class AuditIntegrityScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditIntegrityScheduler.class);

    private final VerificadorDeAuditoria verificador;
    private final TaskMonitorService taskMonitor;
    private final TransactionTemplate transaccion;

    public AuditIntegrityScheduler(
            VerificadorDeAuditoria verificador,
            TaskMonitorService taskMonitor,
            TransactionTemplate transaccion) {
        this.verificador = verificador;
        this.taskMonitor = taskMonitor;
        this.transaccion = transaccion;
    }

    /**
     * Todas las noches a las 3:50 (hora española), después de las otras tres.
     *
     * La transacción va con {@link TransactionTemplate} y no con
     * {@code @Transactional}, por el mismo motivo que en {@link
     * IncompleteTimeEntryScheduler}: el monitor tiene que envolver la
     * transacción entera, o un fallo al confirmar el punto de control quedaría
     * registrado como ejecución correcta.
     */
    @Scheduled(cron = ScheduledTask.CRON_VERIFICACION_INTEGRIDAD, zone = ScheduledTask.ZONA)
    public void verificarLaCadena() {
        taskMonitor.ejecutar(ScheduledTask.VERIFICACION_INTEGRIDAD, () -> {
            AuditIntegrityResponse resultado = transaccion.execute(
                    estado -> verificador.verificarLoNuevoYAnotar());

            if (resultado == null) {
                return "No se pudo comprobar la cadena.";
            }
            if (!resultado.intacta()) {
                // Que la tarea acabe en ERROR es la intención: así sale en
                // /estado/tareas y en el workflow nocturno sin que nadie tenga
                // que leer los logs.
                log.error("CADENA DE AUDITORÍA ROTA en el movimiento {}: {}",
                        resultado.primerFallo(), resultado.motivo());
                throw new IllegalStateException("Cadena de auditoría rota en el movimiento "
                        + resultado.primerFallo() + ": " + resultado.motivo());
            }
            return "Cadena intacta: " + resultado.movimientos() + " movimientos ("
                    + resultado.comprobados() + " recalculados, " + resultado.soloEnlace() + " solo enlace).";
        });
    }
}
