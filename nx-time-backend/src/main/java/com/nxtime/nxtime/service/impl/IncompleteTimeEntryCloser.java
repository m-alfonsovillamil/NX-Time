package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.service.ProjectAllocationService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Cierra una jornada que nadie cerró: la regla, en un solo sitio.
 *
 * La aplican dos caminos, y tienen que dejar exactamente lo mismo:
 * <ul>
 *   <li>el proceso de las 3:00 ({@code IncompleteTimeEntryScheduler}), que
 *       barre todas las olvidadas;</li>
 *   <li>el propio fichaje ({@code TimeEntryServiceImpl}), cuando quien ficha
 *       tiene una abierta desde hace más del límite. Sin esto, qué quedaba
 *       registrado dependía de si las 3:00 habían caído en medio: una jornada
 *       abierta a las 22:00 y olvidada seguía «en curso» toda la tarde
 *       siguiente, y al terminarla a mano salía una jornada de 20 horas sin
 *       ninguna marca.</li>
 * </ul>
 *
 * Qué deja:
 * <ul>
 *   <li>{@code horaSalida} en el límite, no en «ahora»: si se abrió el lunes
 *       y esto corre el miércoles, dar por buenas 48 horas trabajadas sería
 *       peor que no hacer nada.</li>
 *   <li>{@code jornadaIncompleta = true}: esa hora de salida es un tope del
 *       sistema, NO un fichaje. Queda señalada para corregirla.</li>
 *   <li>La traza en la auditoría con {@code modificadoPor = null}, que en esa
 *       tabla significa «acción automática del sistema» (V4), también cuando
 *       lo desencadena alguien al fichar: la hora la ha puesto el sistema.</li>
 * </ul>
 */
@Component
public class IncompleteTimeEntryCloser {

    /**
     * A partir de cuántas horas abierta se considera olvidada. 16 h cubre de
     * sobra cualquier jornada legal (incluidos turnos largos y guardias) sin
     * llegar a las 24, para que una jornada olvidada se detecte esa misma
     * noche y no a los dos días.
     */
    public static final int HORAS_PARA_CONSIDERARLA_OLVIDADA = 16;

    private final TimeEntryRepository timeEntryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TimeEntrySnapshotSerializer snapshotSerializer;
    private final ProjectAllocationService projectAllocationService;

    public IncompleteTimeEntryCloser(
            TimeEntryRepository timeEntryRepository,
            ApplicationEventPublisher eventPublisher,
            TimeEntrySnapshotSerializer snapshotSerializer,
            ProjectAllocationService projectAllocationService) {
        this.timeEntryRepository = timeEntryRepository;
        this.eventPublisher = eventPublisher;
        this.snapshotSerializer = snapshotSerializer;
        this.projectAllocationService = projectAllocationService;
    }

    /** Las jornadas abiertas antes de este instante están olvidadas. */
    public static Instant limite(Instant ahora) {
        return ahora.minus(HORAS_PARA_CONSIDERARLA_OLVIDADA, ChronoUnit.HOURS);
    }

    /** Sigue abierta y entró hace más del límite. */
    public static boolean estaOlvidada(TimeEntry entrada, Instant ahora) {
        return entrada.getHoraSalida() == null && entrada.getHoraEntrada().isBefore(limite(ahora));
    }

    /**
     * Cierra la jornada con la salida en el tope. Quien llama pone la
     * transacción.
     */
    public void cerrar(TimeEntry entrada) {
        String antes = snapshotSerializer.toJson(entrada);

        // La salida se fija al límite, no a Instant.now(): ver el Javadoc de
        // la clase.
        entrada.setHoraSalida(entrada.getHoraEntrada().plus(HORAS_PARA_CONSIDERARLA_OLVIDADA, ChronoUnit.HOURS));
        entrada.setJornadaIncompleta(true);
        // Si se quedó "en pausa", también hay que sacarla de ese estado: si
        // no, quedaría cerrada pero marcada en pausa, un estado que no
        // significa nada.
        entrada.setEnPausa(false);
        entrada.setInicioPausaActual(null);
        timeEntryRepository.save(entrada);
        // Cerrada por el sistema, pero cerrada: sus horas se imputan igual.
        projectAllocationService.alCerrar(entrada);

        eventPublisher.publishEvent(new TimeEntryAuditEvent(TimeEntryAudit.builder()
                .registro(entrada)
                .usuario(entrada.getUsuario())
                .modificadoPor(null) // acción automática, sin autor humano
                .accion(AuditAction.MODIFICACION)
                .valorAnterior(antes)
                .valorNuevo(snapshotSerializer.toJson(entrada))
                .motivo("Cierre automático: jornada sin fichaje de salida tras "
                        + HORAS_PARA_CONSIDERARLA_OLVIDADA + " horas.")
                .build()));
    }
}
