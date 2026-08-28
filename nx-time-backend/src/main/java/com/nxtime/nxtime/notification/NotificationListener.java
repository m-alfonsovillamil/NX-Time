package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.config.AsyncConfig;
import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.User;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía las notificaciones por correo (Fase 10).
 *
 * Dos decisiones que van juntas y conviene leer como una sola:
 *
 *  - **{@code AFTER_COMMIT}**: el correo sale cuando la operación ya
 *    está confirmada en base de datos. Si saliera antes y la
 *    transacción acabara haciendo rollback, habríamos avisado a un
 *    empleado de que le han aprobado unas vacaciones que en realidad no
 *    existen -- y un correo no se puede "deshacer".
 *
 *  - **{@code @Async}**: el envío no bloquea la petición HTTP. Hablar
 *    con un servidor SMTP puede tardar segundos, y quien aprueba una
 *    ausencia no tiene por qué esperar a que salga el correo.
 *
 * Es exactamente lo CONTRARIO de lo que hace {@link
 * com.nxtime.nxtime.audit.TimeEntryAuditListener}, que corre
 * {@code BEFORE_COMMIT} y de forma síncrona: allí, si no se puede
 * auditar, no se ficha. La diferencia no es incoherencia, es que una
 * traza de auditoría es un requisito legal y un correo es una cortesía.
 *
 * {@link EmailSender} además se traga los fallos de SMTP: llegados a
 * este punto la operación de negocio ya está cerrada y no hay nada que
 * revertir.
 */
@Component
public class NotificationListener {

    private final EmailSender emailSender;

    public NotificationListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAbsenceRequested(NotificationEvents.AbsenceRequested evento) {
        AbsenceRequest peticion = evento.peticion();
        emailSender.enviar(
                evento.emailDestino(),
                "Nueva petición de ausencia de " + peticion.getUsuario().getNombre(),
                "absence-requested",
                variables(
                        "nombreGestor", evento.nombreDestino(),
                        "nombreEmpleado", peticion.getUsuario().getNombre(),
                        "tipo", peticion.getTipo(),
                        "fechaInicio", peticion.getFechaInicio(),
                        "fechaFin", peticion.getFechaFin(),
                        "motivo", peticion.getMotivo()));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAbsenceResolved(NotificationEvents.AbsenceResolved evento) {
        AbsenceRequest peticion = evento.peticion();
        boolean aprobada = peticion.getEstado() == AbsenceStatus.APROBADA;
        User empleado = peticion.getUsuario();

        emailSender.enviar(
                empleado.getEmail(),
                "Tu petición de ausencia ha sido " + (aprobada ? "aprobada" : "rechazada"),
                "absence-resolved",
                variables(
                        "nombreEmpleado", empleado.getNombre(),
                        "aprobada", aprobada,
                        "tipo", peticion.getTipo(),
                        "fechaInicio", peticion.getFechaInicio(),
                        "fechaFin", peticion.getFechaFin(),
                        "resolutor", peticion.getAprobadoPor() != null
                                ? peticion.getAprobadoPor().getNombre() : "un gestor",
                        "comentario", peticion.getComentarioResolucion()));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeCreated(NotificationEvents.EmployeeCreated evento) {
        User empleado = evento.empleado();
        emailSender.enviar(
                empleado.getEmail(),
                "Bienvenido a NX Time",
                "employee-welcome",
                variables(
                        "nombreEmpleado", empleado.getNombre(),
                        "nombreEmpresa", evento.nombreEmpresa(),
                        "email", empleado.getEmail()));
    }

    /**
     * Map.of() no admite valores null y aquí varios lo son de forma
     * legítima (el motivo de una ausencia es opcional, el comentario de
     * una aprobación también). LinkedHashMap sí los admite, y Thymeleaf
     * los resuelve como vacíos sin protestar.
     */
    private Map<String, Object> variables(Object... clavesYValores) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < clavesYValores.length; i += 2) {
            mapa.put((String) clavesYValores[i], clavesYValores[i + 1]);
        }
        return mapa;
    }
}
