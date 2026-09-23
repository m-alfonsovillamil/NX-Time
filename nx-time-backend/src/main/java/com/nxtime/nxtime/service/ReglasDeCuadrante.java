package com.nxtime.nxtime.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
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
