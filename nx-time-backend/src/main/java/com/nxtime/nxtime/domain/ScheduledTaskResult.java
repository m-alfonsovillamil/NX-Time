package com.nxtime.nxtime.domain;

/** Cómo acabó una ejecución de una {@link ScheduledTask}. */
public enum ScheduledTaskResult {

    /**
     * Ha empezado y no ha terminado. Si una fila se queda así, el proceso
     * murió a mitad: un reinicio o un despliegue de Render.
     */
    EN_CURSO,

    OK,

    ERROR
}
