package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.ScheduledTaskResult;
import com.nxtime.nxtime.domain.ScheduledTaskRun;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledTaskRunRepository extends JpaRepository<ScheduledTaskRun, Long> {

    Optional<ScheduledTaskRun> findFirstByTareaOrderByInicioDesc(ScheduledTask tarea);

    Optional<ScheduledTaskRun> findFirstByTareaAndResultadoOrderByInicioDesc(
            ScheduledTask tarea, ScheduledTaskResult resultado);

    /** ¿Ha terminado con este resultado alguna ejecución que empezara desde {@code desde}? */
    boolean existsByTareaAndResultadoAndInicioGreaterThanEqual(
            ScheduledTask tarea, ScheduledTaskResult resultado, Instant desde);
}
