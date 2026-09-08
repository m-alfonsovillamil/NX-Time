package com.nxtime.nxtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * La aritmética de los dos umbrales (Fase F).
 *
 * Se prueba con números concretos y sin base de datos porque las
 * preguntas que importan son de aritmética: "¿esta semana con un puente
 * se pasa o no?". Si esto solo se pudiera comprobar levantando el
 * contexto de Spring, en la práctica no se comprobaría.
 */
@DisplayName("OvertimeCalculator")
class OvertimeCalculatorTest {

    private static final BigDecimal JORNADA_COMPLETA = new BigDecimal("40.0");
    private static final BigDecimal JORNADA_REDUCIDA = new BigDecimal("37.5");

    @Nested
    @DisplayName("Umbral diario (art. 34.3 ET)")
    class Diario {

        @Test
        @DisplayName("una jornada normal de ocho horas no genera nada")
        void jornadaNormal() {
            assertThat(OvertimeCalculator.excesoDiario(8 * 60)).isZero();
        }

        @Test
        @DisplayName("nueve horas justas todavía no se pasan: el límite es 'más de'")
        void nueveHorasJustas() {
            assertThat(OvertimeCalculator.excesoDiario(9 * 60)).isZero();
        }

        @Test
        @DisplayName("veinte minutos de más se los come la tolerancia")
        void dentroDeLaTolerancia() {
            // Pasarse un rato no es una hora extra: es fichar al llegar a
            // la mesa en vez de al entrar por la puerta.
            assertThat(OvertimeCalculator.excesoDiario(9 * 60 + 20)).isZero();
        }

        @Test
        @DisplayName("media hora justa tampoco: la tolerancia es inclusiva")
        void justoEnLaTolerancia() {
            assertThat(OvertimeCalculator.excesoDiario(9 * 60 + 30)).isZero();
        }

        @Test
        @DisplayName("pasada la tolerancia se cuenta el exceso ENTERO, no lo que sobra de ella")
        void pasadaLaTolerancia() {
            // 31 minutos de más son 31 minutos de exceso, no 1. La
            // tolerancia decide SI se avisa, no cuánto se avisa: si
            // restara, un aviso de 31 minutos diría "1 min" y no habría
            // manera de entenderlo.
            assertThat(OvertimeCalculator.excesoDiario(9 * 60 + 31)).isEqualTo(31);
        }

        @Test
        @DisplayName("el límite diario NO depende de la jornada contratada")
        void esUnLimiteLegal() {
            // Diez horas y media son un exceso de hora y media para todo
            // el mundo. Quien está a media jornada se pasa de las nueve
            // horas del art. 34.3 igual que quien la tiene completa: es
            // un límite de salud laboral, no de contrato.
            assertThat(OvertimeCalculator.excesoDiario(10 * 60 + 30)).isEqualTo(90);
        }
    }

    @Nested
    @DisplayName("Umbral semanal (prorrateado)")
    class Semanal {

        @Test
        @DisplayName("una semana completa se mide contra la jornada entera")
        void semanaCompleta() {
            assertThat(OvertimeCalculator.objetivoSemanal(JORNADA_COMPLETA, 5)).isEqualTo(40 * 60);
            assertThat(OvertimeCalculator.excesoSemanal(40 * 60, JORNADA_COMPLETA, 5)).isZero();
        }

        @Test
        @DisplayName("una semana con puente se mide contra CUATRO quintos, no contra la jornada entera")
        void semanaConPuente() {
            // Es la razón de ser de todo esto. Quien trabaja sus ocho
            // horas de lunes a jueves ha hecho 32 h, que es exactamente
            // lo que se espera de esa semana. Medirlo contra 40 h daría
            // un falso negativo permanente en cada puente.
            assertThat(OvertimeCalculator.objetivoSemanal(JORNADA_COMPLETA, 4)).isEqualTo(32 * 60);
            assertThat(OvertimeCalculator.excesoSemanal(32 * 60, JORNADA_COMPLETA, 4)).isZero();
        }

        @Test
        @DisplayName("en la semana del puente, pasarse de las 32 h SÍ es exceso")
        void semanaConPuenteQueSePasa() {
            // El otro lado de la misma moneda: prorratear no es sinónimo
            // de ser más permisivo. Trabajar 36 h en una semana de cuatro
            // días son cuatro horas de más aunque no lleguen a 40.
            assertThat(OvertimeCalculator.excesoSemanal(36 * 60, JORNADA_COMPLETA, 4))
                    .isEqualTo(4 * 60);
        }

        @Test
        @DisplayName("una semana entera de vacaciones no puede generar horas extra")
        void semanaSinDiasHabiles() {
            // Y sobre todo: no divide entre cero. Si alguien fichó algo
            // esa semana es un dato raro que hay que mirar, pero no es
            // esta pantalla la que tiene que gritar.
            assertThat(OvertimeCalculator.excesoSemanal(10 * 60, JORNADA_COMPLETA, 0)).isZero();
            assertThat(OvertimeCalculator.objetivoSemanal(JORNADA_COMPLETA, 0)).isZero();
        }

        @Test
        @DisplayName("una jornada de 37,5 h prorratea sin perder el medio minuto")
        void jornadaConDecimales() {
            // 37,5 / 5 * 4 en coma flotante da 29,999999999999996, y
            // truncar eso son 1799 minutos: un minuto de exceso inventado
            // cada semana. Por eso el cálculo va en BigDecimal.
            assertThat(OvertimeCalculator.objetivoSemanal(JORNADA_REDUCIDA, 4)).isEqualTo(30 * 60);
            assertThat(OvertimeCalculator.excesoSemanal(30 * 60, JORNADA_REDUCIDA, 4)).isZero();
        }

        @Test
        @DisplayName("un día suelto de una jornada de 37,5 h son 7,5 h")
        void unSoloDiaHabil() {
            assertThat(OvertimeCalculator.objetivoSemanal(JORNADA_REDUCIDA, 1)).isEqualTo(450);
        }

        @Test
        @DisplayName("sin jornada contratada no se inventa un objetivo")
        void sinJornada() {
            assertThat(OvertimeCalculator.objetivoSemanal(null, 5)).isZero();
        }
    }

    @Nested
    @DisplayName("Bolsa anual (art. 35.2 ET)")
    class Bolsa {

        @Test
        @DisplayName("el tope son 80 horas")
        void tope() {
            assertThat(OvertimeCalculator.MINUTOS_BOLSA_ANUAL).isEqualTo(80 * 60);
        }

        @Test
        @DisplayName("lo que queda nunca es negativo")
        void nuncaNegativa() {
            // Pasarse del tope es posible en la vida real (lo que no es
            // legal es que la empresa lo exija), y una bolsa en negativo
            // solo serviría para pintar una barra rota.
            assertThat(OvertimeCalculator.minutosRestantesDeLaBolsa(100 * 60)).isZero();
            assertThat(OvertimeCalculator.minutosRestantesDeLaBolsa(60 * 60)).isEqualTo(20 * 60);
        }

        @Test
        @DisplayName("el aviso salta al 80 %, no al agotarse")
        void avisaAntesDeLlegar() {
            // Enterarse de que te has pasado del tope cuando ya te has
            // pasado no le sirve a nadie: el aviso existe para poder no
            // llegar.
            assertThat(OvertimeCalculator.bolsaCercaDelLimite(63 * 60)).isFalse();
            assertThat(OvertimeCalculator.bolsaCercaDelLimite(64 * 60)).isTrue();
        }
    }
}
