package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.Complaint;
import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.JobApplication;
import com.nxtime.nxtime.domain.JobPosting;
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

    // ------------------------------------------------------------------
    // Fase G: canal de denuncias
    // ------------------------------------------------------------------
    // Los dos llevan la {@link Complaint} entera y aun así el listener
    // NO puede volcar su contenido en el mensaje: ver más abajo.

    /**
     * Ha entrado una denuncia. Va a quien la instruye.
     *
     * <b>El aviso dice que hay una denuncia, nunca de qué va ni de
     * quién.</b> El destino de esto es un correo — texto plano por una
     * red que no controlamos, guardado después en un buzón personal y en
     * el de un servidor ajeno —, y el relato de un acoso no tiene nada
     * que hacer ahí. Para leerlo hay que entrar en la aplicación, que es
     * donde el acceso está limitado a quien tiene {@code
     * denuncia:instruir} y donde queda registrado.
     *
     * La lista de destinatarios llega ya resuelta y <b>sin excluir a
     * nadie</b>, ni siquiera a quien acaba de presentarla: el servicio
     * explica por qué (un avisado de menos delata al denunciante).
     */
    public record ComplaintReceived(Complaint denuncia, List<User> destinatarios) {
    }

    /**
     * Se ha movido algo en una denuncia: un mensaje o un cambio de
     * estado.
     *
     * {@code novedad} es una frase corta y genérica que compone el
     * servicio ("Han respondido en tu denuncia"). El listener no la
     * deduce del expediente por lo mismo de arriba: cuanto menos sepa el
     * canal de salida, menos hay que revisar cuando alguien añada un
     * caso nuevo.
     *
     * Puede venir con la lista <b>vacía</b>, y no es un error: si la
     * denuncia es anónima no hay a quién avisar. El servicio ni siquiera
     * publica el evento en ese caso, pero el listener no da nada por
     * supuesto.
     */
    public record ComplaintUpdated(
            Complaint denuncia, List<User> destinatarios, String novedad) {
    }

    // ------------------------------------------------------------------
    // Fase H: ofertas internas y candidaturas
    // ------------------------------------------------------------------

    /**
     * Se ha publicado una vacante interna. Va a <b>toda la plantilla</b>.
     *
     * Es la única notificación masiva del proyecto, y contradice a
     * propósito lo que decidió la fase F sobre no avisar de cada hecho
     * detectado. La diferencia es la frecuencia: los excesos de jornada
     * salen a decenas al mes y una vacante interna a unas pocas al año.
     * Con esa frecuencia, avisar es lo que hace que el tablón funcione —
     * uno que nadie sabe que existe deja fuera justo a quien podría dar
     * el paso.
     *
     * Se excluye a quien la publica: nadie necesita que le avisen de lo
     * que acaba de hacer. Aquí sí se puede excluir, al revés que en las
     * denuncias, porque quién publica una oferta es público.
     */
    public record JobPostingPublished(JobPosting oferta, List<User> destinatarios) {
    }

    /**
     * Alguien se ha presentado. Va a <b>quien publicó la oferta</b>, no
     * a todo el que pueda valorar candidaturas.
     *
     * La vacante es de alguien concreto, y mandarle a cada gestor de la
     * empresa un correo por cada persona que opta a un puesto que no es
     * suyo es la forma de que dejen de leerlos. El resto llega igual a
     * la lista de candidatos cuando entra a mirarla.
     */
    public record JobApplicationReceived(JobApplication candidatura, List<User> destinatarios) {
    }

    /**
     * Han movido una candidatura. Va al candidato.
     *
     * Lleva el comentario aparte y no dentro de la entidad porque es lo
     * único que el correo tiene que contar además del estado, y en un
     * descarte es la mitad del mensaje.
     */
    public record JobApplicationUpdated(JobApplication candidatura, List<User> destinatarios) {
    }
}
