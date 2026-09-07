package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.OvertimeAlert;
import com.nxtime.nxtime.domain.User;
import java.util.List;

/**
 * Eventos que disparan una notificación (Fase 10; desde la Fase A,
 * correo Y aviso dentro de la aplicación).
 *
 * Son records con los datos ya resueltos, no ids: quien los publica
 * está dentro de la transacción y tiene las entidades cargadas, mientras
 * que quien los consume ({@link NotificationListener}) corre DESPUÉS del
 * commit y en otro hilo -- si solo recibiera ids tendría que volver a
 * consultar la base de datos, y con la sesión de JPA ya cerrada
 * cualquier relación perezosa reventaría.
 */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    /**
     * Un empleado ha pedido una ausencia: se avisa a quien deba
     * aprobarla. Se publica uno por aprobador.
     *
     * Lleva el {@link User} destinatario entero y no su email y su
     * nombre sueltos, como hasta la Fase A: el aviso in-app necesita el
     * id para escribir {@code avisos.destinatario_id}, y quien lo
     * publica ({@code AbsenceServiceImpl.createRequest}) ya está
     * iterando sobre objetos {@code User}. Pasar la entidad es además
     * más fiel a la regla que enuncia esta clase -- datos resueltos, no
     * referencias que haya que volver a consultar.
     */
    public record AbsenceRequested(AbsenceRequest peticion, User destinatario) {
    }

    /** Un gestor ha aprobado o rechazado una ausencia: se avisa al empleado. */
    public record AbsenceResolved(AbsenceRequest peticion) {
    }

    /** Se ha dado de alta a un empleado: se le da la bienvenida. */
    public record EmployeeCreated(User empleado, String nombreEmpresa) {
    }

    // ------------------------------------------------------------------
    // Fase E: correcciones con aprobación
    // ------------------------------------------------------------------
    // Los tres llevan la lista de destinatarios YA RESUELTA, y no un rol
    // o una authority, por lo mismo que el resto de eventos de aquí: el
    // listener corre @Async y fuera de la sesión JPA, así que no puede
    // salir a buscar a nadie. Quién debe enterarse depende de quién pidió
    // la corrección, y eso solo lo sabe el servicio.

    /** Alguien ha pedido corregir un fichaje. Va a quien tenga que resolverla. */
    public record CorrectionRequested(CorrectionRequest solicitud, List<User> destinatarios) {
    }

    /** Aprobada o rechazada. Va a quien la pidió. */
    public record CorrectionResolved(CorrectionRequest solicitud, List<User> destinatarios) {
    }

    /** El dueño no la acepta: escala a quien resuelve disputas. */
    public record CorrectionDisputed(CorrectionRequest solicitud, List<User> destinatarios) {
    }

    // ------------------------------------------------------------------
    // Fase F: horas extra
    // ------------------------------------------------------------------

    /**
     * El proceso nocturno ha detectado un exceso de jornada.
     *
     * <b>Va solo al empleado, no a quien revisa</b>, y esa asimetría es
     * deliberada. Una empresa mediana genera decenas de excesos al mes;
     * mandarle cada uno por correo a cada gestor es spam por diseño, y un
     * buzón que se ignora es peor que no avisar. Quien revisa trabaja
     * desde una COLA — el contador de avisos abiertos del panel y la
     * bandeja de {@code GET /api/v1/horas-extra/equipo} —, que es como se
     * lleva un trabajo recurrente.
     *
     * El empleado sí lo recibe uno a uno porque para él no es
     * recurrente: es su martes, y es el único que sabe si fue una
     * intensiva pactada o un fichaje mal cerrado. Avisarle antes de que
     * nadie decida es lo que le da tiempo a pedir la corrección.
     *
     * Se mantiene la lista de destinatarios en vez de un solo usuario
     * porque el listener no tiene por qué saber esa regla; hoy trae uno.
     */
    public record OvertimeDetected(OvertimeAlert aviso, List<User> destinatarios) {
    }

    /**
     * La bolsa anual del art. 35.2 ET se acerca al tope.
     *
     * Lleva las cifras ya calculadas y no el aviso que la cruzó, porque
     * el mensaje no habla de ese exceso concreto sino del año entero.
     */
    public record OvertimeBalanceNearLimit(
            User empleado,
            int anio,
            int minutosConsumidos,
            int minutosDisponibles,
            List<User> destinatarios) {
    }
}
