package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una ejecución de una tarea programada (paso 5 del piloto).
 *
 * No pertenece a ninguna empresa: las tareas nocturnas recorren todas a
 * la vez, así que su registro es del sistema.
 */
@Entity(name = "ejecuciones_tarea")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledTaskRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ScheduledTask tarea;

    private Instant inicio;

    /** Null mientras está {@link ScheduledTaskResult#EN_CURSO}. */
    private Instant fin;

    @Enumerated(EnumType.STRING)
    private ScheduledTaskResult resultado;

    private String detalle;
}
