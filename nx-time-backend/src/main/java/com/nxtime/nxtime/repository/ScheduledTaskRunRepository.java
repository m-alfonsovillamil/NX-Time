package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.ScheduledTaskResult;
import com.nxtime.nxtime.domain.ScheduledTaskRun;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledTaskRunRepository extends JpaRepository<ScheduledTaskRun, Long> {

    Optional<ScheduledTaskRun> findFirstByTareaOrderByInicioDesc(ScheduledTask tarea);

    Optional<ScheduledTaskRun> findFirstByTareaAndResultadoOrderByInicioDesc(
            ScheduledTask tarea, ScheduledTaskResult resultado);

    /** La última que no tiene este resultado: con EN_CURSO, la última que terminó, bien o mal. */
    Optional<ScheduledTaskRun> findFirstByTareaAndResultadoNotOrderByInicioDesc(
            ScheduledTask tarea, ScheduledTaskResult resultado);

    /** ¿Ha terminado con este resultado alguna ejecución que empezara desde {@code desde}? */
    boolean existsByTareaAndResultadoAndInicioGreaterThanEqual(
            ScheduledTask tarea, ScheduledTaskResult resultado, Instant desde);

    /**
     * ¿Ha empezado esta tarea OTRA instancia desde {@code desde}? Acabara como
     * acabara: una tarea que otra instancia dejó a medias o en error tampoco
     * se repite sola, porque varias mandan correos (ADR 042).
     *
     * Las ejecuciones sin instancia (anteriores a V42) no cuentan: en SQL,
     * {@code instancia <> :instancia} no es cierto para un nulo.
     */
    boolean existsByTareaAndInicioGreaterThanEqualAndInstanciaNot(
            ScheduledTask tarea, Instant desde, String instancia);

    /**
     * Pide el turno de una tarea: quien llega mientras otra instancia lo
     * tiene, espera a que termine SU transacción (que es corta: mirar y
     * apuntar, no la tarea entera).
     *
     * Un advisory lock de transacción, como el de la cadena de auditoría (ver
     * {@link TimeEntryAuditRepository#bloquearCadena}): se suelta solo al
     * confirmar o deshacer. Y de transacción, no de sesión, a propósito:
     * producción llega a la base por el pooler de Neon, que reparte las
     * conexiones por transacción, y un cerrojo de sesión se quedaría en una
     * conexión que ya está sirviendo a otro.
     *
     * Con dos enteros y no con uno: PostgreSQL lleva las claves de dos enteros
     * aparte de las de un {@code bigint}, así que esta no puede coincidir con
     * la de la cadena.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(:espacio, :clave)", nativeQuery = true)
    void pedirTurno(@Param("espacio") int espacio, @Param("clave") int clave);
}
