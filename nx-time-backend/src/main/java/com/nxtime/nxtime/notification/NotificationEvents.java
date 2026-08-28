package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.User;

/**
 * Eventos que disparan una notificación por correo (Fase 10).
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

    /** Un empleado ha pedido una ausencia: se avisa a quien deba aprobarla. */
    public record AbsenceRequested(AbsenceRequest peticion, String emailDestino, String nombreDestino) {
    }

    /** Un gestor ha aprobado o rechazado una ausencia: se avisa al empleado. */
    public record AbsenceResolved(AbsenceRequest peticion) {
    }

    /** Se ha dado de alta a un empleado: se le da la bienvenida. */
    public record EmployeeCreated(User empleado, String nombreEmpresa) {
    }
}
