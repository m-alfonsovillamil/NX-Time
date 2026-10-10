package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.audit.VerificadorDeAuditoria;
import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.ScheduledTaskResult;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.service.TaskMonitorService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <b>Y una vez por semana, entera</b> (ADR 042). Lo incremental no vuelve
 * nunca sobre una fila ya comprobada: quien cambiase una de hace un mes no
 * saldría en ninguna noche. La madrugada del domingo se recorre la traza desde
 * el principio.
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

    /** El día del recorrido completo: el de menos fichajes. */
    static final DayOfWeek DIA_DEL_RECORRIDO_COMPLETO = DayOfWeek.SUNDAY;

    private static final ZoneId ZONA = ZoneId.of(ScheduledTask.ZONA);

    private final VerificadorDeAuditoria verificador;
    private final TaskMonitorService taskMonitor;
    private final TransactionTemplate transaccion;
    private final Clock clock;

    @Autowired
    public AuditIntegrityScheduler(
            VerificadorDeAuditoria verificador,
            TaskMonitorService taskMonitor,
            TransactionTemplate transaccion) {
        this(verificador, taskMonitor, transaccion, Clock.systemUTC());
    }

    AuditIntegrityScheduler(
            VerificadorDeAuditoria verificador,
            TaskMonitorService taskMonitor,
            TransactionTemplate transaccion,
            Clock clock) {
        this.verificador = verificador;
        this.taskMonitor = taskMonitor;
        this.transaccion = transaccion;
        this.clock = clock;
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
        // Antes de empezar: dentro, la última ejecución ya sería esta.
        boolean entera = tocaEntera();
        taskMonitor.ejecutar(ScheduledTask.VERIFICACION_INTEGRIDAD, () -> {
            AuditIntegrityResponse resultado = transaccion.execute(estado -> entera
                    ? verificador.verificarTodoYAnotar()
                    : verificador.verificarLoNuevoYAnotar());

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
            return (entera ? "Cadena intacta, recorrida entera: " : "Cadena intacta: ")
                    + resultado.movimientos() + " movimientos ("
                    + resultado.comprobados() + " recalculados, " + resultado.soloEnlace() + " solo enlace).";
        });
    }

    /**
     * El domingo, y también <b>si la última vez acabó en error</b>.
     *
     * Lo segundo es lo que impide que un fallo se apague solo. Si el recorrido
     * del domingo encuentra cambiada una fila vieja, la noche del lunes --que
     * solo miraría lo nuevo-- saldría en verde, y el aviso habría durado un
     * día. Así, mientras la última ejecución sea un error se vuelve a recorrer
     * entera, y sigue en rojo hasta que alguien lo mire. Si el error fue otra
     * cosa (la base caída esa noche), cuesta un recorrido de más.
     */
    private boolean tocaEntera() {
        return clock.instant().atZone(ZONA).getDayOfWeek() == DIA_DEL_RECORRIDO_COMPLETO
                || taskMonitor.ultimoResultado(ScheduledTask.VERIFICACION_INTEGRIDAD)
                        .filter(ScheduledTaskResult.ERROR::equals)
                        .isPresent();
    }
}
