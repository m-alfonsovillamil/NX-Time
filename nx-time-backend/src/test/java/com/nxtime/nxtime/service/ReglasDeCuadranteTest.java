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
}
