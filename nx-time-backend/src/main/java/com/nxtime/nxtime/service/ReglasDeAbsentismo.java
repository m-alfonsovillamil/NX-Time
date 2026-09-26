package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.service.JornadaTeoricaService.DiaTeorico;
import com.nxtime.nxtime.service.JornadaTeoricaService.Origen;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;

/**
 * Qué fue cada día de cada persona, para la analítica (Fase B4). Reglas puras,
 * sin base de datos, como {@link ReglasDeCuadrante} y {@code OvertimeCalculator}.
 *
 * <p>La definición, en orden:
 * <ol>
 *   <li><b>¿Se debía trabajar?</b> Lo dice lo PLANIFICADO
 *       ({@link JornadaTeoricaService#planificadoDeVarios}), sin mirar
 *       ausencias: un festivo no; con cuadrante, si ese día tiene horas; sin
 *       cuadrante, de lunes a viernes, que es la misma regla que
 *       {@code WorkingDayService} y que el objetivo semanal de las horas extra.</li>
 *   <li><b>Vacaciones</b>: el día sale de la cuenta. No son absentismo en
 *       ninguna definición que se use en España, y contarlas en el
 *       denominador haría que agosto pareciera un mes ejemplar.</li>
 *   <li><b>Una jornada fichada</b>, o un <b>viaje de trabajo</b>: trabajado. El
 *       fichaje manda sobre una ausencia que no sean vacaciones: quien tenía
 *       aprobada una consulta médica y luego fue a trabajar, trabajó, y
 *       contarle el día como perdido inflaría el absentismo. El viaje es
 *       trabajar en otro sitio, aunque no se fiche.</li>
 *   <li><b>Cualquier otra ausencia aprobada</b>: día perdido CON motivo, y el
 *       motivo se desglosa.</li>
 *   <li><b>Una ausencia de cuadrante con la explicación aceptada</b> (B2):
 *       perdido con motivo.</li>
 *   <li>Si no, <b>sin fichaje ni ausencia</b>. No se llama "injustificado": el
 *       sistema no sabe por qué, solo que no consta nada. Puede ser un olvido
 *       de fichar.</li>
 * </ol>
 *
 * <b>Se mide en días, no en horas</b>, porque las ausencias de este sistema
 * son de días enteros y un día de baja de alguien a media jornada es un día
 * perdido, no cuatro horas. Ver ADR 026.
 */
public final class ReglasDeAbsentismo {

    /** El motivo con el que se desglosa una ausencia de cuadrante aceptada. */
    public static final String INCIDENCIA_ACEPTADA = "INCIDENCIA_ACEPTADA";

    private ReglasDeAbsentismo() {
    }

    public enum Clase {
        /** No se debía trabajar: fuera de la cuenta. */
        NO_LABORABLE,
        /** Se debía trabajar, pero estaba de vacaciones: fuera de la cuenta. */
        VACACIONES,
        TRABAJADO,
        AUSENCIA_JUSTIFICADA,
        SIN_FICHAJE;

        /** Si el día entra en el denominador del absentismo. */
        public boolean cuenta() {
            return this != NO_LABORABLE && this != VACACIONES;
        }

        public boolean perdido() {
            return this == AUSENCIA_JUSTIFICADA || this == SIN_FICHAJE;
        }
    }

    /**
     * @param ausencia el tipo de la ausencia aprobada de ese día, o null.
     * @param motivo con qué se desglosa si es {@link Clase#AUSENCIA_JUSTIFICADA}:
     *     el nombre del tipo de ausencia o {@link #INCIDENCIA_ACEPTADA}.
     */
    public record Dia(Clase clase, String motivo) {
    }

    public static Dia clasificar(
            DiaTeorico planificado, AbsenceType ausencia, boolean fichado, boolean ausenciaAceptada) {
        if (!laborable(planificado)) {
            return new Dia(Clase.NO_LABORABLE, null);
        }
        if (ausencia == AbsenceType.VACACIONES) {
            return new Dia(Clase.VACACIONES, null);
        }
        if (fichado || ausencia == AbsenceType.VIAJE_TRABAJO) {
            return new Dia(Clase.TRABAJADO, null);
        }
        if (ausencia != null) {
            return new Dia(Clase.AUSENCIA_JUSTIFICADA, ausencia.name());
        }
        if (ausenciaAceptada) {
            return new Dia(Clase.AUSENCIA_JUSTIFICADA, INCIDENCIA_ACEPTADA);
        }
        return new Dia(Clase.SIN_FICHAJE, null);
    }

    /** Si se debía trabajar ese día según lo planificado, sin mirar ausencias. */
    public static boolean laborable(DiaTeorico planificado) {
        if (planificado.origen() == Origen.NO_LABORABLE) {
            return false;
        }
        if (planificado.tieneCuadrante()) {
            return planificado.minutos() > 0;
        }
        DayOfWeek dia = planificado.fecha().getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY;
    }

    /**
     * Un porcentaje con un decimal, o null si no hay sobre qué calcularlo.
     *
     * Null y no cero, por lo mismo que el contador de denuncias del panel: un
     * 0 % afirma que nadie faltó, y en un periodo sin días laborables no se
     * puede afirmar nada.
     */
    public static BigDecimal porcentaje(long parte, long total) {
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(parte * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    /** Qué se lee para cada motivo del desglose. */
    public static String etiqueta(String motivo) {
        if (INCIDENCIA_ACEPTADA.equals(motivo)) {
            return "Ausencia de cuadrante explicada y aceptada";
        }
        try {
            return AbsenceType.valueOf(motivo).getEtiqueta();
        } catch (IllegalArgumentException desconocido) {
            return motivo;
        }
    }
}
