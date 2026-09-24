package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cuánto y cuándo debía trabajar una persona un día (Fase B1).
 *
 * Es la <b>única</b> pieza que contesta a eso. Junta tres fuentes que ya
 * existían o que llegan con los cuadrantes, en este orden de precedencia:
 *
 * <ol>
 *   <li><b>{@link NonWorkingDayService}</b>: un festivo o una ausencia
 *       aprobada hacen el día no laborable, diga lo que diga el cuadrante. No
 *       se copia esa regla aquí: se le pregunta.</li>
 *   <li><b>Una excepción de ese día</b>: un día libre pactado, o un horario
 *       distinto solo ese día.</li>
 *   <li><b>La plantilla asignada</b> ese día, con su vigencia.</li>
 * </ol>
 *
 * Si no hay ninguna de las tres, el día es {@link Origen#SIN_CUADRANTE}: la
 * persona tiene jornada contratada ({@code horasSemanales}) pero no horario, y
 * eso no se inventa repartiendo las horas entre los días. <b>El cuadrante no
 * sustituye a la jornada contratada: la complementa.</b> La jornada es el
 * cuánto; el cuadrante, el cuándo (ver ADR 023).
 */
public interface JornadaTeoricaService {

    /** De dónde sale el horario teórico de un día. */
    enum Origen {
        /** Festivo o ausencia aprobada. Cero minutos, con su motivo. */
        NO_LABORABLE,
        /** Una excepción de ese día manda sobre la plantilla. */
        EXCEPCION,
        /** Lo que dice la plantilla asignada. Un día sin tramos es libre. */
        CUADRANTE,
        /** La persona no tiene cuadrante ese día. */
        SIN_CUADRANTE
    }

    /** Un tramo de trabajo, en minutos desde la medianoche del día en que empieza. */
    record Tramo(int inicio, int fin) {

        public int minutos() {
            return fin - inicio;
        }

        public LocalTime horaInicio() {
            return ReglasDeCuadrante.aHora(inicio);
        }

        public LocalTime horaFin() {
            return ReglasDeCuadrante.aHora(fin);
        }

        /** Si termina al día siguiente: el turno de noche. */
        public boolean cruzaMedianoche() {
            return fin > ReglasDeCuadrante.MINUTOS_DIA;
        }
    }

    /**
     * El horario teórico de un día.
     *
     * @param motivo por qué no se trabaja (festivo, ausencia) o qué dice la
     *   excepción; null si no hay nada que explicar.
     * @param plantilla el nombre de la plantilla vigente ese día, si la hay.
     *   Va también en los días de excepción: sigue siendo su cuadrante.
     */
    record DiaTeorico(
            LocalDate fecha,
            Origen origen,
            int minutos,
            List<Tramo> tramos,
            String motivo,
            String plantilla) {

        /** A qué hora debía entrar: el inicio del primer tramo, si trabaja. */
        public Optional<LocalTime> entrada() {
            return tramos.stream().findFirst().map(Tramo::horaInicio);
        }

        /** Si tiene horario teórico, sea de la plantilla o de una excepción. */
        public boolean tieneCuadrante() {
            return origen == Origen.CUADRANTE || origen == Origen.EXCEPCION;
        }
    }

    /** Día a día, ambos extremos incluidos. */
    List<DiaTeorico> dias(User persona, LocalDate desde, LocalDate hasta);

    DiaTeorico dia(User persona, LocalDate fecha);

    /**
     * El horario teórico de un día para muchas personas, por id, con un número
     * de consultas que NO depende de cuántas sean (Fase B2).
     *
     * Para el barrido nocturno de incidencias, que recorre cada noche a toda la
     * plantilla con cuadrante de todas las empresas. La precedencia es la misma
     * función que usa {@link #dias}: aquí solo cambia cómo se cargan los datos.
     */
    Map<Long, DiaTeorico> diaDeVarios(Collection<User> personas, LocalDate fecha);

    /** Cero si no se trabaja ese día o si no hay cuadrante. */
    default int minutosTeoricos(User persona, LocalDate fecha) {
        return dia(persona, fecha).minutos();
    }

    default Optional<LocalTime> entradaPrevista(User persona, LocalDate fecha) {
        return dia(persona, fecha).entrada();
    }

    /**
     * Los minutos que se esperan de una semana, de lunes a domingo.
     *
     * Los días con cuadrante suman lo que dice el cuadrante. Los días sin él
     * —toda la semana, si la persona no tiene cuadrante; o los anteriores a
     * que se le asignara— cuentan como siempre: su parte de la jornada
     * contratada, prorrateada por los días hábiles con
     * {@link OvertimeCalculator#objetivoSemanal}.
     *
     * <b>Una semana sin ningún día de cuadrante da exactamente el mismo número
     * que el cálculo de horas extra de siempre.</b> Es el requisito que hace que
     * esta fase no cambie nada a quien no tenga cuadrante.
     */
    long minutosTeoricosSemana(User persona, LocalDate lunes);
}
