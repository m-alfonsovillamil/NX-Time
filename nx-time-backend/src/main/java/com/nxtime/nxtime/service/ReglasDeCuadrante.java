package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.ScheduleIncidentType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Las reglas de un cuadrante, sin base de datos (Fase B1).
 *
 * Mismo criterio que {@link OvertimeCalculator} y {@link ReglasDePausa}: lo
 * que se puede razonar con números concretos se prueba con números concretos,
 * sin levantar nada.
 *
 * <p>Los horarios van en <b>minutos desde medianoche</b> del día en que empieza
 * el tramo, no en {@link LocalTime}. Un turno de 22:00 a 06:00 es
 * {@code [1320, 1800)}: el fin puede pasar de 1440, y es lo que permite
 * escribirlo en una sola fila y comparar solapes con enteros.
 */
public final class ReglasDeCuadrante {

    private ReglasDeCuadrante() {
    }

    public static final int MINUTOS_DIA = 24 * 60;

    public static final int MINUTOS_SEMANA = 7 * MINUTOS_DIA;

    /**
     * Cuánto puede separarse una plantilla de la jornada contratada sin avisar.
     *
     * La misma tolerancia que las horas extra, y por el mismo motivo: media
     * hora a la semana es redondeo, no una discrepancia que alguien tenga que
     * revisar.
     */
    public static final int TOLERANCIA_JORNADA_MINUTOS = OvertimeCalculator.TOLERANCIA_MINUTOS;

    /** Un tramo de una plantilla semanal. */
    public record TramoSemanal(DayOfWeek dia, int inicio, int fin) {

        public int minutos() {
            return fin - inicio;
        }

        /** Dónde empieza y acaba dentro de la semana, en minutos desde el lunes a las 00:00. */
        int inicioEnLaSemana() {
            return (dia.getValue() - 1) * MINUTOS_DIA + inicio;
        }

        int finEnLaSemana() {
            return (dia.getValue() - 1) * MINUTOS_DIA + fin;
        }
    }

    /**
     * Lo primero que está mal en unos tramos, o vacío si están bien.
     *
     * Comprueba lo mismo que los CHECK de V31 —para devolver un 400 que se
     * entienda en vez de un 409 genérico— y además lo único que la base no
     * puede comprobar: <b>el solape entre días</b>. Un turno del lunes de 22:00
     * a 06:00 termina el martes, y pisa cualquier tramo del martes que empiece
     * antes de las 06:00. El EXCLUDE de V31 compara tramos del MISMO día, así
     * que eso se le escapa. Incluido el del domingo que termina el lunes: la
     * semana se repite, así que el lunes siguiente es este mismo lunes.
     */
    public static Optional<String> problemaEn(List<TramoSemanal> tramos) {
        for (TramoSemanal tramo : tramos) {
            Optional<String> problema = problemaEnElTramo(tramo.inicio(), tramo.fin());
            if (problema.isPresent()) {
                return Optional.of(nombreDelDia(tramo.dia()) + ": " + problema.get());
            }
        }

        // Todos los tramos en minutos de la semana, ordenados. Si alguno se
        // sale por el final (domingo que acaba el lunes), se añade también su
        // trozo al principio de la semana, que es donde pisaría.
        List<int[]> enLaSemana = new ArrayList<>();
        for (TramoSemanal tramo : tramos) {
            enLaSemana.add(new int[] {tramo.inicioEnLaSemana(), tramo.finEnLaSemana(), tramo.dia().getValue()});
            if (tramo.finEnLaSemana() > MINUTOS_SEMANA) {
                enLaSemana.add(new int[] {0, tramo.finEnLaSemana() - MINUTOS_SEMANA, tramo.dia().getValue()});
            }
        }
        enLaSemana.sort(Comparator.<int[]>comparingInt(t -> t[0]).thenComparingInt(t -> t[1]));

        for (int i = 1; i < enLaSemana.size(); i++) {
            int[] anterior = enLaSemana.get(i - 1);
            int[] actual = enLaSemana.get(i);
            // Semiabiertos, como int4range: acabar a las 14:00 y empezar a
            // las 14:00 no es pisarse.
            if (actual[0] < anterior[1]) {
                return Optional.of(anterior[2] == actual[2]
                        ? "hay dos tramos del " + nombreDelDia(DayOfWeek.of(actual[2])) + " que se pisan."
                        : "el tramo del " + nombreDelDia(DayOfWeek.of(anterior[2]))
                                + " termina después de que empiece el del "
                                + nombreDelDia(DayOfWeek.of(actual[2])) + ".");
            }
        }
        return Optional.empty();
    }

    /**
     * Lo que está mal en un tramo suelto, o vacío. Sirve también para las
     * excepciones de un día, que siguen las mismas reglas.
     */
    public static Optional<String> problemaEnElTramo(int inicio, int fin) {
        if (inicio < 0 || inicio >= MINUTOS_DIA) {
            return Optional.of("un tramo tiene que empezar entre las 00:00 y las 23:59.");
        }
        if (fin <= inicio) {
            return Optional.of("un tramo tiene que terminar después de empezar.");
        }
        if (fin - inicio > MINUTOS_DIA) {
            return Optional.of("un tramo no puede durar más de 24 horas.");
        }
        return Optional.empty();
    }

    /**
     * Si dos tramos del mismo día se pisan. Para las excepciones, que son de un
     * solo día y no se repiten cada semana.
     */
    public static Optional<String> solapeEnUnDia(List<int[]> tramos) {
        List<int[]> ordenados = new ArrayList<>(tramos);
        ordenados.sort(Comparator.comparingInt(t -> t[0]));
        for (int i = 1; i < ordenados.size(); i++) {
            if (ordenados.get(i)[0] < ordenados.get(i - 1)[1]) {
                return Optional.of("hay dos tramos que se pisan.");
            }
        }
        return Optional.empty();
    }

    public static long minutosSemanales(List<TramoSemanal> tramos) {
        return tramos.stream().mapToLong(TramoSemanal::minutos).sum();
    }

    /**
     * Si una plantilla se separa de la jornada contratada más de la tolerancia.
     *
     * La jornada contratada de una semana completa es la que dice
     * {@link OvertimeCalculator#objetivoSemanal} con cinco días hábiles, que es
     * exactamente lo que representan las horas semanales del contrato. Así las
     * dos cuentas no pueden llegar a números distintos por redondear distinto.
     *
     * Sin jornada contratada no hay con qué comparar, y no se avisa.
     */
    public static boolean difiereDeLaJornada(long minutosPlantilla, BigDecimal horasSemanales) {
        if (horasSemanales == null) {
            return false;
        }
        long contratados = OvertimeCalculator.objetivoSemanal(horasSemanales, 5);
        return Math.abs(minutosPlantilla - contratados) > TOLERANCIA_JORNADA_MINUTOS;
    }

    // ------------------------------------------------------------------
    // Incidencias (Fase B2)
    // ------------------------------------------------------------------

    /**
     * Cuánto tarde se puede llegar sin que sea un retraso.
     *
     * Diez minutos, no cero: es la diferencia entre fichar al entrar por la
     * puerta o al llegar a la mesa, o el autobús. Sin margen, cualquier
     * cuadrante generaría una incidencia casi a diario, y una bandeja llena de
     * incidencias que nadie mira es peor que no tenerla.
     */
    public static final int TOLERANCIA_RETRASO_MINUTOS = 10;

    /** La misma tolerancia para salir antes, y por lo mismo. */
    public static final int TOLERANCIA_SALIDA_MINUTOS = 10;

    /**
     * Un fichaje de un día, lo justo para comparar.
     *
     * @param salida null si la jornada sigue abierta (el turno de noche cuando
     *   el barrido pasa de madrugada).
     * @param cerradoPorElSistema lo cerró el cierre automático de las 3:00, y
     *   su salida no es un dato real.
     */
    public record FichajeDelDia(long registroId, Instant entrada, Instant salida, boolean cerradoPorElSistema) {
    }

    /** Lo que el barrido encuentra un día, antes de guardarlo. */
    public record IncidenciaDetectada(
            ScheduleIncidentType tipo, int minutos, int horaPrevista, Instant horaReal, Long registroId) {
    }

    /**
     * Qué no cuadró un día entre el horario teórico y lo fichado.
     *
     * <ul>
     *   <li><b>Ausencia</b>: tocaba trabajar y no hay ningún fichaje. Los
     *       festivos y las ausencias aprobadas ya llegan con cero minutos
     *       teóricos (los resuelve NonWorkingDayService), así que nunca
     *       producen una ausencia aquí. Esa garantía vive en un solo sitio.</li>
     *   <li><b>Retraso</b>: la primera entrada, contra el inicio del primer
     *       tramo. En una jornada partida no se mira la vuelta de comer: el
     *       horario del mediodía es de quien lo trabaja.</li>
     *   <li><b>Salida anticipada</b>: la última salida, contra el fin del
     *       último tramo. Solo si todas las jornadas del día están cerradas y
     *       ninguna la cerró el sistema.</li>
     * </ul>
     *
     * Las horas se calculan en reloj de pared de la zona: el tramo de las
     * 09:00 empieza a las 09:00 también el día del cambio de hora.
     *
     * Los fichajes se asignan al día en que EMPIEZAN, igual que las horas
     * extra. Un turno de noche que se ficha pasada la medianoche cuenta para
     * el día siguiente: es la misma frontera que ya usa todo el sistema.
     */
    public static List<IncidenciaDetectada> incidencias(
            LocalDate fecha,
            ZoneId zona,
            List<JornadaTeoricaService.Tramo> tramos,
            int minutosTeoricos,
            List<FichajeDelDia> fichajes) {

        if (tramos.isEmpty() || minutosTeoricos <= 0) {
            return List.of();
        }
        int inicioPrevisto = tramos.stream().mapToInt(JornadaTeoricaService.Tramo::inicio).min().orElseThrow();
        int finPrevisto = tramos.stream().mapToInt(JornadaTeoricaService.Tramo::fin).max().orElseThrow();

        if (fichajes.isEmpty()) {
            return List.of(new IncidenciaDetectada(
                    ScheduleIncidentType.AUSENCIA, minutosTeoricos, inicioPrevisto, null, null));
        }

        List<IncidenciaDetectada> encontradas = new ArrayList<>();

        FichajeDelDia primero = fichajes.stream().min(Comparator.comparing(FichajeDelDia::entrada)).orElseThrow();
        long retraso = Duration.between(instante(fecha, zona, inicioPrevisto), primero.entrada()).toMinutes();
        if (retraso > TOLERANCIA_RETRASO_MINUTOS) {
            encontradas.add(new IncidenciaDetectada(
                    ScheduleIncidentType.RETRASO, (int) retraso, inicioPrevisto,
                    primero.entrada(), primero.registroId()));
        }

        boolean salidaFiable = fichajes.stream()
                .allMatch(fichaje -> fichaje.salida() != null && !fichaje.cerradoPorElSistema());
        if (salidaFiable) {
            FichajeDelDia ultimo = fichajes.stream().max(Comparator.comparing(FichajeDelDia::salida)).orElseThrow();
            long adelanto = Duration.between(ultimo.salida(), instante(fecha, zona, finPrevisto)).toMinutes();
            if (adelanto > TOLERANCIA_SALIDA_MINUTOS) {
                encontradas.add(new IncidenciaDetectada(
                        ScheduleIncidentType.SALIDA_ANTICIPADA, (int) adelanto, finPrevisto,
                        ultimo.salida(), ultimo.registroId()));
            }
        }
        return encontradas;
    }

    /**
     * El instante en que cae un minuto del día, en reloj de pared.
     *
     * Con {@code LocalDateTime}, no sumando minutos a un instante: sumar 540
     * minutos a la medianoche del día del cambio de hora daría las 08:00 o las
     * 10:00, no las 09:00. Un minuto más allá de 1440 cae al día siguiente.
     */
    static Instant instante(LocalDate fecha, ZoneId zona, int minutos) {
        return fecha.atStartOfDay().plusMinutes(minutos).atZone(zona).toInstant();
    }

    /** La hora de reloj de un minuto del día, dando la vuelta tras la medianoche. */
    public static LocalTime aHora(int minutos) {
        int delDia = Math.floorMod(minutos, MINUTOS_DIA);
        return LocalTime.of(delDia / 60, delDia % 60);
    }

    /** {@code 540} → {@code 09:00}. {@code 1800} → {@code 06:00}. */
    public static String hora(int minutos) {
        LocalTime hora = aHora(minutos);
        return String.format("%02d:%02d", hora.getHour(), hora.getMinute());
    }

    /** {@code 2250} → «37 h 30 min». Para los mensajes, no para calcular. */
    public static String duracion(long minutos) {
        long h = minutos / 60;
        long m = minutos % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    private static String nombreDelDia(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> "lunes";
            case TUESDAY -> "martes";
            case WEDNESDAY -> "miércoles";
            case THURSDAY -> "jueves";
            case FRIDAY -> "viernes";
            case SATURDAY -> "sábado";
            case SUNDAY -> "domingo";
        };
    }
}
