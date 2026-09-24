package com.nxtime.nxtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.service.ReglasDeCuadrante.TramoSemanal;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Las reglas de un cuadrante, con números concretos (Fase B1).
 *
 * La parte que más importa es el solape <b>entre días</b>: es lo único que la
 * base no puede comprobar, porque el EXCLUDE de V31 compara tramos del mismo
 * día. Un turno de noche del lunes termina el martes; si nada lo mirara, la
 * plantilla guardaría dos horarios superpuestos y las horas teóricas de esa
 * semana contarían dos veces la misma madrugada.
 */
class ReglasDeCuadranteTest {

    private static TramoSemanal tramo(DayOfWeek dia, String inicio, String fin) {
        return new TramoSemanal(dia, minutos(inicio), minutos(fin));
    }

    /** "22:00" → 1320. "+06:00" → 1800, del día siguiente. */
    private static int minutos(String hora) {
        boolean diaSiguiente = hora.startsWith("+");
        LocalTime t = LocalTime.parse(diaSiguiente ? hora.substring(1) : hora);
        return t.getHour() * 60 + t.getMinute() + (diaSiguiente ? 1440 : 0);
    }

    @Nested
    @DisplayName("Tramos que están bien")
    class Validos {

        @Test
        @DisplayName("La oficina de lunes a viernes, con jornada partida")
        void oficinaPartida() {
            List<TramoSemanal> tramos = List.of(
                    tramo(DayOfWeek.MONDAY, "09:00", "14:00"), tramo(DayOfWeek.MONDAY, "15:00", "18:00"),
                    tramo(DayOfWeek.FRIDAY, "08:00", "15:00"));
            assertThat(ReglasDeCuadrante.problemaEn(tramos)).isEmpty();
        }

        @Test
        @DisplayName("Acabar a las 14:00 y empezar a las 14:00 no es pisarse")
        void contiguos() {
            assertThat(ReglasDeCuadrante.problemaEn(List.of(
                    tramo(DayOfWeek.MONDAY, "09:00", "14:00"),
                    tramo(DayOfWeek.MONDAY, "14:00", "15:00")))).isEmpty();
        }

        @Test
        @DisplayName("Un turno de noche seguido de otro al día siguiente, sin tocarse")
        void nochesSeguidas() {
            assertThat(ReglasDeCuadrante.problemaEn(List.of(
                    tramo(DayOfWeek.MONDAY, "22:00", "+06:00"),
                    tramo(DayOfWeek.TUESDAY, "22:00", "+06:00")))).isEmpty();
        }

        @Test
        @DisplayName("El turno de noche del lunes y un tramo del martes que empieza justo al acabar")
        void nocheYManana() {
            assertThat(ReglasDeCuadrante.problemaEn(List.of(
                    tramo(DayOfWeek.MONDAY, "22:00", "+06:00"),
                    tramo(DayOfWeek.TUESDAY, "06:00", "10:00")))).isEmpty();
        }

        @Test
        @DisplayName("Una plantilla vacía es válida: todos los días libres")
        void vacia() {
            assertThat(ReglasDeCuadrante.problemaEn(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Tramos que se pisan")
    class Solapes {

        @Test
        @DisplayName("Dos tramos del mismo día")
        void mismoDia() {
            assertThat(ReglasDeCuadrante.problemaEn(List.of(
                    tramo(DayOfWeek.WEDNESDAY, "09:00", "14:00"),
                    tramo(DayOfWeek.WEDNESDAY, "13:00", "17:00"))))
                    .hasValueSatisfying(problema -> assertThat(problema).contains("miércoles").contains("se pisan"));
        }

        /**
         * El caso que justifica que esta comprobación exista: el EXCLUDE de la
         * base lo deja pasar, porque los dos tramos son de días distintos.
         */
        @Test
        @DisplayName("El turno de noche del lunes pisa el martes que empieza antes de las 06:00")
        void nochePisaAlDiaSiguiente() {
            assertThat(ReglasDeCuadrante.problemaEn(List.of(
                    tramo(DayOfWeek.MONDAY, "22:00", "+06:00"),
                    tramo(DayOfWeek.TUESDAY, "05:00", "09:00"))))
                    .hasValueSatisfying(problema -> assertThat(problema)
                            .contains("lunes").contains("martes"));
        }

        /**
         * La semana se repite: el lunes siguiente al domingo es este mismo
         * lunes. Sin tratar la vuelta, este choque pasaría desapercibido.
         */
        @Test
        @DisplayName("El turno de noche del domingo pisa el lunes (la semana da la vuelta)")
        void domingoPisaElLunes() {
            assertThat(ReglasDeCuadrante.problemaEn(List.of(
                    tramo(DayOfWeek.MONDAY, "05:00", "13:00"),
                    tramo(DayOfWeek.SUNDAY, "22:00", "+06:00"))))
                    .hasValueSatisfying(problema -> assertThat(problema)
                            .contains("domingo").contains("lunes"));
        }

        @Test
        @DisplayName("El turno de noche del domingo no pisa un lunes que empieza después")
        void domingoNoPisaSiNoToca() {
            assertThat(ReglasDeCuadrante.problemaEn(List.of(
                    tramo(DayOfWeek.MONDAY, "09:00", "17:00"),
                    tramo(DayOfWeek.SUNDAY, "22:00", "+06:00")))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Tramos mal formados")
    class MalFormados {

        @Test
        void empiezaALas24() {
            assertThat(ReglasDeCuadrante.problemaEnElTramo(1440, 1500)).isPresent();
        }

        @Test
        void acabaAntesDeEmpezar() {
            assertThat(ReglasDeCuadrante.problemaEnElTramo(600, 600)).isPresent();
            assertThat(ReglasDeCuadrante.problemaEnElTramo(600, 500)).isPresent();
        }

        @Test
        void duraMasDeUnDia() {
            assertThat(ReglasDeCuadrante.problemaEnElTramo(0, 1441)).isPresent();
            assertThat(ReglasDeCuadrante.problemaEnElTramo(0, 1440)).isEmpty();
        }

        @Test
        @DisplayName("El problema de un tramo dice de qué día es")
        void diceElDia() {
            assertThat(ReglasDeCuadrante.problemaEn(List.of(new TramoSemanal(DayOfWeek.THURSDAY, 600, 500))))
                    .hasValueSatisfying(problema -> assertThat(problema).startsWith("jueves"));
        }
    }

    @Nested
    @DisplayName("Contra la jornada contratada")
    class Jornada {

        private static final BigDecimal CUARENTA = new BigDecimal("40");

        @Test
        @DisplayName("Una plantilla de 40 h con jornada de 40 h no avisa")
        void cuadra() {
            assertThat(ReglasDeCuadrante.difiereDeLaJornada(40 * 60, CUARENTA)).isFalse();
        }

        @Test
        @DisplayName("Media hora de diferencia es redondeo: no avisa")
        void dentroDeLaTolerancia() {
            assertThat(ReglasDeCuadrante.difiereDeLaJornada(40 * 60 - 30, CUARENTA)).isFalse();
            assertThat(ReglasDeCuadrante.difiereDeLaJornada(40 * 60 + 30, CUARENTA)).isFalse();
        }

        @Test
        @DisplayName("Más de media hora, por arriba o por abajo, avisa")
        void fueraDeLaTolerancia() {
            assertThat(ReglasDeCuadrante.difiereDeLaJornada(40 * 60 - 31, CUARENTA)).isTrue();
            assertThat(ReglasDeCuadrante.difiereDeLaJornada(40 * 60 + 31, CUARENTA)).isTrue();
        }

        @Test
        @DisplayName("37,5 h: la cuenta no se tuerce por decimales")
        void jornadaConDecimales() {
            assertThat(ReglasDeCuadrante.difiereDeLaJornada(2250, new BigDecimal("37.5"))).isFalse();
        }

        @Test
        @DisplayName("Sin jornada contratada no hay con qué comparar")
        void sinJornada() {
            assertThat(ReglasDeCuadrante.difiereDeLaJornada(10, null)).isFalse();
        }
    }

    @Test
    @DisplayName("Las horas se leen como en el reloj, también pasada la medianoche")
    void horasDeReloj() {
        assertThat(ReglasDeCuadrante.hora(540)).isEqualTo("09:00");
        assertThat(ReglasDeCuadrante.hora(1800)).isEqualTo("06:00");
        assertThat(ReglasDeCuadrante.hora(0)).isEqualTo("00:00");
        assertThat(ReglasDeCuadrante.duracion(2250)).isEqualTo("37 h 30 min");
        assertThat(ReglasDeCuadrante.duracion(2400)).isEqualTo("40 h");
    }

    @Test
    void minutosSemanales() {
        assertThat(ReglasDeCuadrante.minutosSemanales(List.of(
                tramo(DayOfWeek.MONDAY, "09:00", "14:00"),
                tramo(DayOfWeek.MONDAY, "22:00", "+06:00")))).isEqualTo(5 * 60 + 8 * 60);
    }

    // ------------------------------------------------------------------
    // Incidencias (Fase B2)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Incidencias de un día")
    class Incidencias {

        private final java.time.ZoneId madrid = java.time.ZoneId.of("Europe/Madrid");
        private final java.time.LocalDate lunes = java.time.LocalDate.of(2026, 10, 5);

        private final List<JornadaTeoricaService.Tramo> oficina =
                List.of(new JornadaTeoricaService.Tramo(minutos("09:00"), minutos("17:00")));

        private java.time.Instant a(java.time.LocalDate dia, String hora) {
            return java.time.ZonedDateTime.of(dia, LocalTime.parse(hora), madrid).toInstant();
        }

        private ReglasDeCuadrante.FichajeDelDia fichaje(String entrada, String salida) {
            return new ReglasDeCuadrante.FichajeDelDia(1L, a(lunes, entrada), salida == null ? null : a(lunes, salida), false);
        }

        private List<ReglasDeCuadrante.IncidenciaDetectada> en(
                List<JornadaTeoricaService.Tramo> tramos, ReglasDeCuadrante.FichajeDelDia... fichajes) {
            int teoricos = tramos.stream().mapToInt(JornadaTeoricaService.Tramo::minutos).sum();
            return ReglasDeCuadrante.incidencias(lunes, madrid, tramos, teoricos, List.of(fichajes));
        }

        @Test
        @DisplayName("Un día normal no produce nada")
        void normal() {
            assertThat(en(oficina, fichaje("08:58", "17:02"))).isEmpty();
        }

        @Test
        @DisplayName("Sin ningún fichaje en un día de trabajo es una ausencia, con los minutos teóricos")
        void ausencia() {
            assertThat(en(oficina)).singleElement().satisfies(incidencia -> {
                assertThat(incidencia.tipo()).isEqualTo(com.nxtime.nxtime.domain.ScheduleIncidentType.AUSENCIA);
                assertThat(incidencia.minutos()).isEqualTo(480);
                assertThat(incidencia.horaReal()).isNull();
            });
        }

        @Test
        @DisplayName("Un día libre en el cuadrante no produce ausencia aunque no se fiche")
        void libre() {
            assertThat(ReglasDeCuadrante.incidencias(lunes, madrid, List.of(), 0, List.of())).isEmpty();
        }

        @Test
        @DisplayName("Diez minutos tarde entran en la tolerancia; once no")
        void tolerancia() {
            assertThat(en(oficina, fichaje("09:10", "17:00"))).isEmpty();
            assertThat(en(oficina, fichaje("09:11", "17:00"))).singleElement()
                    .satisfies(incidencia -> {
                        assertThat(incidencia.tipo()).isEqualTo(com.nxtime.nxtime.domain.ScheduleIncidentType.RETRASO);
                        assertThat(incidencia.minutos()).isEqualTo(11);
                        assertThat(incidencia.horaPrevista()).isEqualTo(540);
                    });
        }

        @Test
        @DisplayName("Salir media hora antes es una salida anticipada")
        void salidaAnticipada() {
            assertThat(en(oficina, fichaje("09:00", "16:30"))).singleElement()
                    .satisfies(incidencia -> {
                        assertThat(incidencia.tipo())
                                .isEqualTo(com.nxtime.nxtime.domain.ScheduleIncidentType.SALIDA_ANTICIPADA);
                        assertThat(incidencia.minutos()).isEqualTo(30);
                    });
        }

        /**
         * La salida de una jornada que cerró el cierre automático de las 3:00 no
         * es un dato real. Acusar de salir pronto con ella sería mentir.
         */
        @Test
        @DisplayName("Una jornada que cerró el sistema no produce salida anticipada")
        void cerradaPorElSistema() {
            var cerrada = new ReglasDeCuadrante.FichajeDelDia(1L, a(lunes, "09:00"), a(lunes, "12:00"), true);
            assertThat(en(oficina, cerrada)).isEmpty();
        }

        @Test
        @DisplayName("Una jornada todavía abierta no produce salida anticipada")
        void abierta() {
            assertThat(en(oficina, fichaje("09:00", null))).isEmpty();
        }

        @Test
        @DisplayName("Jornada partida: primera entrada contra el primer tramo, última salida contra el último")
        void partida() {
            var partida = List.of(
                    new JornadaTeoricaService.Tramo(minutos("09:00"), minutos("14:00")),
                    new JornadaTeoricaService.Tramo(minutos("15:00"), minutos("18:00")));
            // Vuelve de comer a las 15:40: eso no se mira.
            assertThat(en(partida, fichaje("09:00", "14:00"), fichaje("15:40", "18:00"))).isEmpty();
            // Pero la salida final sí.
            assertThat(en(partida, fichaje("09:00", "14:00"), fichaje("15:00", "17:15")))
                    .extracting(ReglasDeCuadrante.IncidenciaDetectada::minutos).containsExactly(45);
        }

        @Test
        @DisplayName("Turno de noche: la salida esperada es a las 06:00 del día siguiente")
        void turnoDeNoche() {
            var noche = List.of(new JornadaTeoricaService.Tramo(minutos("22:00"), minutos("+06:00")));
            var fichado = new ReglasDeCuadrante.FichajeDelDia(
                    1L, a(lunes, "22:20"), a(lunes.plusDays(1), "05:30"), false);

            assertThat(en(noche, fichado))
                    .extracting(ReglasDeCuadrante.IncidenciaDetectada::tipo,
                            ReglasDeCuadrante.IncidenciaDetectada::minutos)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(
                                    com.nxtime.nxtime.domain.ScheduleIncidentType.RETRASO, 20),
                            org.assertj.core.groups.Tuple.tuple(
                                    com.nxtime.nxtime.domain.ScheduleIncidentType.SALIDA_ANTICIPADA, 30));
        }

        /**
         * El 29 de marzo de 2026 los relojes pasan de las 2:00 a las 3:00. Sumando
         * 540 minutos a la medianoche, "las 09:00" serían las 10:00, y quien
         * entrara a las 09:05 saldría adelantado; con reloj de pared, llega cinco
         * minutos tarde, dentro de la tolerancia.
         */
        @Test
        @DisplayName("El día del cambio de hora, las 09:00 siguen siendo las 09:00")
        void cambioDeHora() {
            java.time.LocalDate cambio = java.time.LocalDate.of(2026, 3, 29);
            var fichado = new ReglasDeCuadrante.FichajeDelDia(1L,
                    java.time.ZonedDateTime.of(cambio, LocalTime.of(9, 25), madrid).toInstant(),
                    java.time.ZonedDateTime.of(cambio, LocalTime.of(17, 0), madrid).toInstant(), false);

            assertThat(ReglasDeCuadrante.incidencias(cambio, madrid, oficina, 480, List.of(fichado)))
                    .singleElement()
                    .satisfies(incidencia -> assertThat(incidencia.minutos()).isEqualTo(25));
        }
    }
}
