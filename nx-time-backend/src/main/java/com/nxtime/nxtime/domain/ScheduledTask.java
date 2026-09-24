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
    HORAS_EXTRA(ScheduledTask.CRON_HORAS_EXTRA),

    /**
     * {@code ScheduleIncidentScheduler}: retrasos, salidas anticipadas y
     * ausencias contra el cuadrante (Fase B2). Después del cierre de las 3:00
     * por lo mismo que las horas extra: la salida de una jornada que cerró el
     * sistema no es un dato real, y hay que saber cuáles son antes de mirar.
     */
    CUMPLIMIENTO_CUADRANTE(ScheduledTask.CRON_CUMPLIMIENTO_CUADRANTE),

    /**
     * {@code DataDeletionScheduler}: anonimiza a quien pidió el borrado de sus
     * datos cuando vencen los cuatro años de conservación (ADR 016). Casi
     * todas las noches no encuentra nada, y eso también se registra: una
     * tarea que no deja rastro no se distingue de una que no corrió.
     */
    ANONIMIZACION(ScheduledTask.CRON_ANONIMIZACION),

    /**
     * {@code AuditIntegrityScheduler}: comprueba la cadena de hashes de la
     * auditoría y deja un punto de control (Fase A4).
     *
     * La última de la noche a propósito: repasa lo que han escrito las
     * anteriores, y así una manipulación no espera a que alguien se acuerde de
     * pulsar el botón de verificar.
     */
    VERIFICACION_INTEGRIDAD(ScheduledTask.CRON_VERIFICACION_INTEGRIDAD);

    /*
     * Constantes y no solo el campo de cada valor: {@code @Scheduled} solo
     * admite expresiones constantes, y el campo de un enum no lo es.
     */
    public static final String CRON_CIERRE_JORNADAS = "0 0 3 * * *";
    public static final String CRON_HORAS_EXTRA = "0 30 3 * * *";
    public static final String CRON_CUMPLIMIENTO_CUADRANTE = "0 40 3 * * *";

    /*
     * 3:45 y no más tarde: el workflow de GitHub que comprueba /estado/tareas
     * corre a las 03:15 UTC, que en invierno son las 4:15 en Madrid. A las 4:00
     * la tarea quedaría justo en el borde de la gracia de 15 minutos.
     */
    public static final String CRON_ANONIMIZACION = "0 45 3 * * *";

    /*
     * 3:50: la última de la noche, cinco minutos después de la anonimización,
     * para repasar lo que han escrito las otras tres.
     *
     * Sigue por debajo de las 4:00 por el mismo motivo que la anterior. El
     * monitor pregunta por "la última hora programada antes de ahora menos la
     * gracia de 15 minutos" (ver TaskMonitorServiceImpl.GRACIA), y el workflow
     * consulta a las 03:15 UTC, que en invierno son las 04:15 en Madrid: una
     * tarea a partir de las 4:00 caería fuera y se daría por no ejecutada
     * cada día de invierno.
     *
     * Es incremental --solo mira lo escrito desde el último punto de control--
     * así que los 25 minutos reales de margen sobran holgadamente.
     */
    public static final String CRON_VERIFICACION_INTEGRIDAD = "0 50 3 * * *";

    /** Todas corren en hora española, con el sistema en calma. */
    public static final String ZONA = "Europe/Madrid";

    private final String cron;

    ScheduledTask(String cron) {
        this.cron = cron;
    }

    public CronExpression cron() {
        return CronExpression.parse(cron);
    }
}
