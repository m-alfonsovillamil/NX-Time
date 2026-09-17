package com.nxtime.nxtime.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.AddedPause;
import com.nxtime.nxtime.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las reglas de una pausa añadida a posteriori (ADR 015). Las usan las dos
 * vías —directa y por aprobación—, así que se prueban aquí una vez.
 *
 * Jornada de referencia: de 09:00 a 17:00 UTC, y "ahora" es después.
 */
class ReglasDePausaTest {

    private static final Instant ENTRADA = Instant.parse("2026-06-01T09:00:00Z");
    private static final Instant SALIDA = Instant.parse("2026-06-01T17:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-06-01T20:00:00Z");

    private static Instant a(String hora) {
        return Instant.parse("2026-06-01T" + hora + ":00Z");
    }

    private static void validar(Instant inicio, Instant fin, long yaDePausa, List<AddedPause> vivas) {
        ReglasDePausa.exigirValida(inicio, fin, ENTRADA, SALIDA, yaDePausa, vivas, null, AHORA);
    }

    @Test
    @DisplayName("Una hora de comida dentro de la jornada vale")
    void unaPausaNormal_vale() {
        assertThatCode(() -> validar(a("14:00"), a("15:00"), 0, List.of())).doesNotThrowAnyException();
    }

    /*
     * Sin tope de duración: se decidió así, con motivo siempre obligatorio.
     * Este test existe para que nadie meta un tope "por prudencia" sin
     * darse cuenta de que contradice esa decisión.
     */
    @Test
    @DisplayName("No hay tope de duración: cuatro horas de pausa en una jornada de ocho valen")
    void sinTopeDeDuracion() {
        assertThatCode(() -> validar(a("10:00"), a("14:00"), 0, List.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una pausa que acaba antes de empezar es un 400")
    void finAntesQueInicio_da400() {
        assertThatThrownBy(() -> validar(a("15:00"), a("14:00"), 0, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("después de empezar");
    }

    @Test
    @DisplayName("Una pausa que se sale de la jornada no es una pausa de esa jornada")
    void fueraDeLaJornada_falla() {
        assertThatThrownBy(() -> validar(a("08:30"), a("09:30"), 0, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dentro de la jornada");
        assertThatThrownBy(() -> validar(a("16:30"), a("17:30"), 0, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dentro de la jornada");
    }

    @Test
    @DisplayName("Una pausa que todavía no ha terminado no se puede añadir")
    void enElFuturo_falla() {
        assertThatThrownBy(() -> ReglasDePausa.exigirValida(
                a("14:00"), a("15:00"), ENTRADA, a("20:00"), 0, List.of(), null, a("14:30")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no ha terminado");
    }

    @Test
    @DisplayName("No se puede solapar con otra pausa añadida")
    void solapeConOtraAnadida_falla() {
        AddedPause comida = AddedPause.builder().inicio(a("14:00")).fin(a("15:00")).build();

        assertThatThrownBy(() -> validar(a("14:30"), a("15:30"), 0, List.of(comida)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("solapa");
        // Tocarse en el extremo no es solaparse: 15:00-15:30 justo después vale.
        assertThatCode(() -> validar(a("15:00"), a("15:30"), 3600, List.of(comida)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("No se puede solapar con la pausa que está en curso ahora mismo")
    void solapeConLaPausaEnCurso_falla() {
        assertThatThrownBy(() -> ReglasDePausa.exigirValida(
                a("15:00"), a("16:00"), ENTRADA, AHORA, 0, List.of(), a("15:30"), AHORA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en curso");
    }

    /*
     * La red que no es redundante: las pausas fichadas con el botón NO tienen
     * intervalo, así que el solape con ellas es invisible. Solo el total
     * impide que la jornada acabe con más pausa que duración —y los
     * agregados del repositorio restan en SQL sin proteger el resultado.
     */
    @Test
    @DisplayName("No puede quedar más pausa que jornada, contando las fichadas sin intervalo")
    void totalMayorQueLaJornada_falla() {
        // Ya lleva 7 h 30 m de pausa fichada; una hora más no cabe en 8 h.
        assertThatThrownBy(() -> validar(a("10:00"), a("11:00"), 27_000, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("más pausa que tiempo");
    }
}
