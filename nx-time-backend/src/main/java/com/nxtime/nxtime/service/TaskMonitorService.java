package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.ScheduledTaskResult;
import com.nxtime.nxtime.dto.SystemStatusResponse;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Registro y vigilancia de las tareas programadas (paso 5 del piloto).
 */
public interface TaskMonitorService {

    /**
     * Ejecuta {@code trabajo} dejando constancia de que empezó y de cómo
     * terminó.
     *
     * <b>No abre transacción, y es a propósito.</b> Si el trabajo es
     * transaccional tiene que confirmar DENTRO de {@code trabajo}: así un
     * fallo al confirmar llega aquí como excepción y queda registrado como
     * ERROR. Con la transacción por fuera, el commit ocurriría después de
     * haber escrito "OK".
     *
     * <b>Una vez por turno, haya las instancias que haya</b> (ADR 042): si
     * otra instancia del servicio ya ha empezado esta tarea en el turno que
     * toca, el trabajo no se ejecuta y no se registra nada. La misma instancia
     * sí puede repetirla.
     *
     * @param trabajo devuelve un resumen legible de lo que hizo
     * @throws RuntimeException la misma que lance el trabajo, ya registrada
     */
    void ejecutar(ScheduledTask tarea, Supplier<String> trabajo);

    /**
     * Cómo acabó la última ejecución de la tarea que llegó a terminar, bien o
     * mal. Vacío si no ha terminado ninguna.
     */
    Optional<ScheduledTaskResult> ultimoResultado(ScheduledTask tarea);

    /** Si cada tarea terminó bien la última vez que le tocaba correr. */
    SystemStatusResponse estado();
}
