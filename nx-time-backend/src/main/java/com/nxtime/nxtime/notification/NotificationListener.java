package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.config.AsyncConfig;
import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.Complaint;
import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.CorrectionStatus;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.OvertimeAlert;
import com.nxtime.nxtime.domain.OvertimeType;
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

    // ------------------------------------------------------------------
    // Fase E: correcciones con aprobación
    // ------------------------------------------------------------------

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCorrectionRequested(NotificationEvents.CorrectionRequested evento) {
        CorrectionRequest solicitud = evento.solicitud();
        String quienPide = solicitud.getSolicitante().getNombre();
        // Que te pidan corregir TU fichaje no es lo mismo que tener que
        // aprobar el de otro: el titular lo dice, porque de él depende
        // si esto es "revisa lo tuyo" o "te toca resolver".
        boolean sobreSuFichaje = !solicitud.laPidioElDueno();
        String titulo = sobreSuFichaje
                ? quienPide + " propone corregir un fichaje tuyo"
                : "Corrección pendiente de " + quienPide;

        for (User destinatario : evento.destinatarios()) {
            avisar(new CreateNoticeCommand(
                    solicitud.getEmpresa().getId(),
                    destinatario.getId(),
                    NoticeType.CORRECCION_SOLICITADA,
                    titulo,
                    solicitud.getMotivo(),
                    NoticeType.CORRECCION_SOLICITADA.getRutaDestinoPorDefecto()));

            emailSender.enviar(
                    destinatario.getEmail(),
                    titulo,
                    "correction-requested",
                    variables(
                            "nombreDestinatario", destinatario.getNombre(),
                            "nombreSolicitante", quienPide,
                            "sobreSuFichaje", sobreSuFichaje,
                            "empleado", solicitud.getDuenoDelFichaje().getNombre(),
                            "motivo", solicitud.getMotivo()));
        }
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCorrectionResolved(NotificationEvents.CorrectionResolved evento) {
        CorrectionRequest solicitud = evento.solicitud();
        boolean aprobada = solicitud.getEstado() == CorrectionStatus.APROBADA;
        String titulo = "Tu corrección ha sido " + (aprobada ? "aprobada" : "rechazada");

        for (User destinatario : evento.destinatarios()) {
            avisar(new CreateNoticeCommand(
                    solicitud.getEmpresa().getId(),
                    destinatario.getId(),
                    NoticeType.CORRECCION_RESUELTA,
                    titulo,
                    solicitud.getComentarioResolucion() != null
                            ? solicitud.getComentarioResolucion() : solicitud.getMotivo(),
                    NoticeType.CORRECCION_RESUELTA.getRutaDestinoPorDefecto()));

            emailSender.enviar(
                    destinatario.getEmail(),
                    titulo,
                    "correction-resolved",
                    variables(
                            "nombreDestinatario", destinatario.getNombre(),
                            "aprobada", aprobada,
                            "resolutor", solicitud.getAprobador() != null
                                    ? solicitud.getAprobador().getNombre() : "quien la ha revisado",
                            "comentario", solicitud.getComentarioResolucion()));
        }
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCorrectionDisputed(NotificationEvents.CorrectionDisputed evento) {
        CorrectionRequest solicitud = evento.solicitud();
        String titulo = solicitud.getDuenoDelFichaje().getNombre()
                + " no acepta una corrección de su fichaje";

        for (User destinatario : evento.destinatarios()) {
            avisar(new CreateNoticeCommand(
                    solicitud.getEmpresa().getId(),
                    destinatario.getId(),
                    NoticeType.CORRECCION_EN_DISPUTA,
                    titulo,
                    solicitud.getMotivoDisputa(),
                    NoticeType.CORRECCION_EN_DISPUTA.getRutaDestinoPorDefecto()));

            emailSender.enviar(
                    destinatario.getEmail(),
                    titulo,
                    "correction-disputed",
                    variables(
                            "nombreDestinatario", destinatario.getNombre(),
                            "empleado", solicitud.getDuenoDelFichaje().getNombre(),
                            "solicitante", solicitud.getSolicitante().getNombre(),
                            "motivoCorreccion", solicitud.getMotivo(),
                            "motivoDisputa", solicitud.getMotivoDisputa()));
        }
    }

    // ------------------------------------------------------------------
    // Fase F: horas extra
    // ------------------------------------------------------------------

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOvertimeDetected(NotificationEvents.OvertimeDetected evento) {
        OvertimeAlert aviso = evento.aviso();
        String periodo = periodo(aviso);
        String exceso = duracion(aviso.getMinutosExtra());
        String trabajado = duracion(aviso.getMinutosEsperados() + aviso.getMinutosExtra());
        String esperado = duracion(aviso.getMinutosEsperados());

        for (User destinatario : evento.destinatarios()) {
            // El mismo hecho se cuenta distinto según a quién: al que
            // hizo las horas en segunda persona, a quien revisa con el
            // nombre delante. Mandar el texto de gestor a quien las hizo
            // se lee como una acusación.
            boolean propio = destinatario.getId() == aviso.getUsuario().getId();
            String titulo = propio
                    ? "Exceso de jornada " + periodo
                    : aviso.getUsuario().getNombre() + ": exceso de jornada " + periodo;

            avisar(new CreateNoticeCommand(
                    aviso.getEmpresa().getId(),
                    destinatario.getId(),
                    NoticeType.HORAS_EXTRA_DETECTADAS,
                    titulo,
                    exceso + " por encima de " + esperado + ". Pendiente de revisar.",
                    NoticeType.HORAS_EXTRA_DETECTADAS.getRutaDestinoPorDefecto()));

            emailSender.enviar(
                    destinatario.getEmail(),
                    titulo,
                    "overtime-detected",
                    variables(
                            "nombreDestinatario", destinatario.getNombre(),
                            "propio", propio,
                            "empleado", aviso.getUsuario().getNombre(),
                            "periodo", periodo,
                            "trabajado", trabajado,
                            "esperado", esperado,
                            "exceso", exceso));
        }
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOvertimeBalanceNearLimit(NotificationEvents.OvertimeBalanceNearLimit evento) {
        User empleado = evento.empleado();
        String consumido = duracion(evento.minutosConsumidos());
        String disponible = duracion(evento.minutosDisponibles());

        for (User destinatario : evento.destinatarios()) {
            boolean propio = destinatario.getId() == empleado.getId();
            String titulo = propio
                    ? "Tu bolsa de horas extra se está agotando"
                    : "La bolsa de horas extra de " + empleado.getNombre() + " se está agotando";

            avisar(new CreateNoticeCommand(
                    empleado.getEmpresa().getId(),
                    destinatario.getId(),
                    NoticeType.BOLSA_HORAS_EXTRA_AL_LIMITE,
                    titulo,
                    consumido + " de las 80 h del año. Quedan " + disponible + ".",
                    NoticeType.BOLSA_HORAS_EXTRA_AL_LIMITE.getRutaDestinoPorDefecto()));

            emailSender.enviar(
                    destinatario.getEmail(),
                    titulo,
                    "overtime-balance-near-limit",
                    variables(
                            "nombreDestinatario", destinatario.getNombre(),
                            "propio", propio,
                            "empleado", empleado.getNombre(),
                            "anio", evento.anio(),
                            "consumido", consumido,
                            "disponible", disponible));
        }
    }

    // ------------------------------------------------------------------
    // Fase G: canal de denuncias
    // ------------------------------------------------------------------
    // Los dos métodos de aquí son deliberadamente los más pobres del
    // fichero: dicen QUE ha pasado algo y en qué expediente, y ni una
    // palabra de lo que la denuncia cuenta ni de quién la puso. El resto
    // de la fase se ha ocupado de que la identidad no esté guardada; de
    // poco serviría si el correo la sacara del sistema por la puerta de
    // atrás.

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onComplaintReceived(NotificationEvents.ComplaintReceived evento) {
        Complaint denuncia = evento.denuncia();
        String titulo = "Nueva denuncia en el canal interno";

        for (User destinatario : evento.destinatarios()) {
            avisar(new CreateNoticeCommand(
                    denuncia.getEmpresa().getId(),
                    destinatario.getId(),
                    NoticeType.DENUNCIA_RECIBIDA,
                    titulo,
                    // La categoría sí: es lo mínimo para priorizar la
                    // bandeja y no dice nada de nadie.
                    denuncia.getCategoria().getEtiqueta()
                            + ". Hay 7 días naturales para acusar recibo.",
                    NoticeType.DENUNCIA_RECIBIDA.getRutaDestinoPorDefecto()));

            emailSender.enviar(
                    destinatario.getEmail(),
                    titulo,
                    "complaint-received",
                    variables(
                            "nombreDestinatario", destinatario.getNombre(),
                            "categoria", denuncia.getCategoria().getEtiqueta(),
                            "fecha", denuncia.getCreadoEn()));
        }
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onComplaintUpdated(NotificationEvents.ComplaintUpdated evento) {
        Complaint denuncia = evento.denuncia();
        String titulo = "Novedad en una denuncia";

        for (User destinatario : evento.destinatarios()) {
            avisar(new CreateNoticeCommand(
                    denuncia.getEmpresa().getId(),
                    destinatario.getId(),
                    NoticeType.DENUNCIA_ACTUALIZADA,
                    titulo,
                    evento.novedad(),
                    NoticeType.DENUNCIA_ACTUALIZADA.getRutaDestinoPorDefecto()));

            emailSender.enviar(
                    destinatario.getEmail(),
                    titulo,
                    "complaint-updated",
                    variables(
                            "nombreDestinatario", destinatario.getNombre(),
                            "novedad", evento.novedad()));
        }
    }

    /**
     * "el martes 3 de marzo" o "la semana del 2 al 8 de marzo".
     *
     * Se compone aquí y no en la plantilla porque las dos plantillas y
     * el aviso in-app necesitan la misma frase, y Thymeleaf no es sitio
     * para una condición sobre el tipo de aviso.
     */
    private String periodo(OvertimeAlert aviso) {
        if (aviso.getTipo() == OvertimeType.SEMANAL) {
            return "de la semana del " + FECHA.format(aviso.getFecha())
                    + " al " + FECHA.format(aviso.getFecha().plusDays(6));
        }
        return "del " + FECHA.format(aviso.getFecha());
    }

    /**
     * Minutos a "10 h 30 min". Se formatea para leer, no para calcular:
     * "10,5 h" obliga a quien lo lee a traducir la parte decimal a
     * minutos, y es justo la clase de cuenta que se hace mal de cabeza.
     */
    private String duracion(int minutos) {
        int horas = minutos / 60;
        int resto = minutos % 60;
        if (horas == 0) {
            return resto + " min";
        }
        return resto == 0 ? horas + " h" : horas + " h " + resto + " min";
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
