package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.config.AsyncConfig;
import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CreateNoticeCommand;
import com.nxtime.nxtime.service.NoticeService;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica las notificaciones de un evento: primero el aviso dentro de
 * la aplicación (Fase A) y después el correo (Fase 10).
 *
 * Tres decisiones que conviene leer como una sola:
 *
 *  - **{@code AFTER_COMMIT}**: se avisa cuando la operación ya está
 *    confirmada en base de datos. Si se avisara antes y la transacción
 *    acabara haciendo rollback, habríamos dicho a un empleado que le
 *    han aprobado unas vacaciones que en realidad no existen -- y un
 *    correo no se puede "deshacer".
 *
 *  - **{@code @Async}**: el envío no bloquea la petición HTTP. Hablar
 *    con un servidor SMTP puede tardar segundos, y quien aprueba una
 *    ausencia no tiene por qué esperar a que salga el correo.
 *
 *  - **El aviso va ANTES que el correo.** El correo puede tardar
 *    segundos (tres timeouts de 5 s en application.yml) o caer en la
 *    política CallerRuns si la cola del executor se llena; el aviso es
 *    un INSERT de milisegundos y es el canal que el usuario ve dentro
 *    de la aplicación. Y con el SMTP caído -- el escenario que motivó
 *    la Fase A -- el aviso queda guardado igualmente.
 *
 * Es exactamente lo CONTRARIO de lo que hace {@link
 * com.nxtime.nxtime.audit.TimeEntryAuditListener}, que corre
 * {@code BEFORE_COMMIT} y de forma síncrona: allí, si no se puede
 * auditar, no se ficha. La diferencia no es incoherencia, es que una
 * traza de auditoría es un requisito legal y un aviso es una cortesía.
 *
 * Por eso mismo la entrega aquí es *at-most-once* y sin reintentos:
 * {@link EmailSender} se traga los fallos de SMTP y {@link
 * #avisar(CreateNoticeCommand)} se traga los de base de datos. Un
 * outbox con reintentos sería desproporcionado para algo que está en la
 * categoría del correo, no en la de la auditoría.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    /**
     * El cuerpo de un aviso lo lee una persona, así que las fechas van
     * en el formato de aquí y no en ISO. Es el mismo que ya usan las
     * plantillas de correo ({@code #temporals.format(..., 'dd/MM/yyyy')}).
     */
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmailSender emailSender;
    private final NoticeService noticeService;

    public NotificationListener(EmailSender emailSender, NoticeService noticeService) {
        this.emailSender = emailSender;
        this.noticeService = noticeService;
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAbsenceRequested(NotificationEvents.AbsenceRequested evento) {
        AbsenceRequest peticion = evento.peticion();
        User destinatario = evento.destinatario();

        avisar(new CreateNoticeCommand(
                peticion.getEmpresa().getId(),
                destinatario.getId(),
                NoticeType.AUSENCIA_SOLICITADA,
                "Nueva petición de " + peticion.getUsuario().getNombre(),
                rango(peticion),
                NoticeType.AUSENCIA_SOLICITADA.getRutaDestinoPorDefecto()));

        emailSender.enviar(
                destinatario.getEmail(),
                "Nueva petición de ausencia de " + peticion.getUsuario().getNombre(),
                "absence-requested",
                variables(
                        "nombreGestor", destinatario.getNombre(),
                        "nombreEmpleado", peticion.getUsuario().getNombre(),
                        "tipo", peticion.getTipo().getEtiqueta(),
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

        avisar(new CreateNoticeCommand(
                peticion.getEmpresa().getId(),
                empleado.getId(),
                NoticeType.AUSENCIA_RESUELTA,
                "Tu ausencia ha sido " + (aprobada ? "aprobada" : "rechazada"),
                rango(peticion) + (peticion.getComentarioResolucion() != null
                        ? ". " + peticion.getComentarioResolucion() : ""),
                NoticeType.AUSENCIA_RESUELTA.getRutaDestinoPorDefecto()));

        emailSender.enviar(
                empleado.getEmail(),
                "Tu petición de ausencia ha sido " + (aprobada ? "aprobada" : "rechazada"),
                "absence-resolved",
                variables(
                        "nombreEmpleado", empleado.getNombre(),
                        "aprobada", aprobada,
                        "tipo", peticion.getTipo().getEtiqueta(),
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

        avisar(new CreateNoticeCommand(
                empleado.getEmpresa().getId(),
                empleado.getId(),
                NoticeType.BIENVENIDA,
                "Bienvenido a " + evento.nombreEmpresa(),
                "Tu cuenta ya está activa. Desde aquí puedes fichar tu jornada y pedir ausencias.",
                NoticeType.BIENVENIDA.getRutaDestinoPorDefecto()));

        emailSender.enviar(
                empleado.getEmail(),
                "Bienvenido a NX Time",
                "employee-welcome",
                variables(
                        "nombreEmpleado", empleado.getNombre(),
                        "nombreEmpresa", evento.nombreEmpresa(),
                        "email", empleado.getEmail()));
    }

    /** "Vacaciones, del 01/03/2027 al 03/03/2027". */
    private String rango(AbsenceRequest peticion) {
        return peticion.getTipo().getEtiqueta()
                + ", del " + FECHA.format(peticion.getFechaInicio())
                + " al " + FECHA.format(peticion.getFechaFin());
    }

    /**
     * Publica el aviso sin dejar que un fallo suyo se lleve por delante
     * el correo.
     *
     * El {@code try/catch} tiene que estar AQUÍ y no dentro de {@link
     * NoticeService#publicar}, aunque {@link EmailSender} sí se trague
     * sus fallos dentro de la clase: {@code publicar} es
     * {@code @Transactional}, y en un método transaccional el commit
     * ocurre en el proxy, DESPUÉS de que el cuerpo del método retorne.
     * Un catch de puertas adentro no puede capturar una violación de
     * clave ajena ni una conexión caída al confirmar; solo uno de
     * puertas afuera lo ve.
     *
     * No se relanza porque estamos AFTER_COMMIT: la operación de
     * negocio ya está cerrada y no hay nada que revertir.
     */
    private void avisar(CreateNoticeCommand comando) {
        try {
            noticeService.publicar(comando);
        } catch (RuntimeException e) {
            log.error("No se pudo publicar el aviso {} para el usuario {}: {}",
                    comando.tipo(), comando.destinatarioId(), e.getMessage());
        }
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
