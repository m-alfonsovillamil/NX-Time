package com.nxtime.nxtime.domain;

import org.springframework.scheduling.support.CronExpression;

/**
 * Las tareas programadas cuya ejecución queda registrada (paso 5 del
 * piloto, 09/2026). Ver V15__ejecuciones_tarea.sql.
 *
 * <b>El cron vive aquí y no solo en el {@code @Scheduled}.</b> Lo leen a
 * la vez el programador, para lanzar la tarea, y
 * {@code TaskMonitorServiceImpl}, para saber cuándo debió correr la última
 * vez. Con dos literales separados, cambiar la hora de una tarea dejaría
 * la comprobación mirando la hora vieja y avisando en falso cada día.
 *
 * Añadir una tarea exige también ampliar el CHECK
 * {@code ck_ejecuciones_tarea_tarea} de la base.
 */
public enum ScheduledTask {

    /** {@code IncompleteTimeEntryScheduler}: cierra las jornadas que nadie cerró. */
    CIERRE_JORNADAS(ScheduledTask.CRON_CIERRE_JORNADAS),

    /**
     * {@code OvertimeScheduler}: detecta excesos de jornada. Media hora
     * después que la anterior a propósito: una jornada abierta no tiene
     * horas que contar.
     */
    HORAS_EXTRA(ScheduledTask.CRON_HORAS_EXTRA);

    /*
     * Constantes y no solo el campo de cada valor: {@code @Scheduled} solo
     * admite expresiones constantes, y el campo de un enum no lo es.
     */
    public static final String CRON_CIERRE_JORNADAS = "0 0 3 * * *";
    public static final String CRON_HORAS_EXTRA = "0 30 3 * * *";

    /** Las dos corren en hora española, con el sistema en calma. */
    public static final String ZONA = "Europe/Madrid";

    private final String cron;

    ScheduledTask(String cron) {
        this.cron = cron;
    }

    public CronExpression cron() {
        return CronExpression.parse(cron);
    }
}
