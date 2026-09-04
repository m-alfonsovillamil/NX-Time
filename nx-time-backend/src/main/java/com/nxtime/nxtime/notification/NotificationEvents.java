package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.User;

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
}
