package com.nxtime.nxtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.service.JornadaTeoricaService.DiaTeorico;
import com.nxtime.nxtime.service.JornadaTeoricaService.Origen;
import com.nxtime.nxtime.service.JornadaTeoricaService.Tramo;
import com.nxtime.nxtime.service.ReglasDeAbsentismo.Clase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Qué fue cada día (Fase B4). Una regla por test, en el orden en que se aplican. */
class ReglasDeAbsentismoTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 9, 21);
    private static final LocalDate SABADO = LocalDate.of(2026, 9, 26);

    private static DiaTeorico sinCuadrante(LocalDate fecha) {
        return new DiaTeorico(fecha, Origen.SIN_CUADRANTE, 0, List.of(), null, null);
    }

    private static DiaTeorico cuadrante(LocalDate fecha, int minutos) {
        List<Tramo> tramos = minutos == 0 ? List.of() : List.of(new Tramo(540, 540 + minutos));
        return new DiaTeorico(fecha, Origen.CUADRANTE, minutos, tramos, null, "Oficina");
    }

    private static Clase clase(DiaTeorico dia, AbsenceType ausencia, boolean fichado, boolean aceptada) {
        return ReglasDeAbsentismo.clasificar(dia, ausencia, fichado, aceptada).clase();
    }

    @Test
    @DisplayName("Sin cuadrante se trabaja de lunes a viernes; un festivo no se trabaja nunca")
    void laborableSinCuadrante() {
        assertThat(clase(sinCuadrante(LUNES), null, false, false)).isEqualTo(Clase.SIN_FICHAJE);
        assertThat(clase(sinCuadrante(SABADO), null, false, false)).isEqualTo(Clase.NO_LABORABLE);
        DiaTeorico festivo = new DiaTeorico(LUNES, Origen.NO_LABORABLE, 0, List.of(), "Festivo", null);
        assertThat(clase(festivo, null, false, false)).isEqualTo(Clase.NO_LABORABLE);
    }

    @Test
    @DisplayName("Con cuadrante manda el cuadrante: el sábado con turno se trabaja y el lunes libre no")
    void laborableConCuadrante() {
        assertThat(clase(cuadrante(SABADO, 240), null, false, false)).isEqualTo(Clase.SIN_FICHAJE);
        assertThat(clase(cuadrante(LUNES, 0), null, false, false)).isEqualTo(Clase.NO_LABORABLE);
    }

    @Test
    @DisplayName("Una ausencia en un día que no se trabajaba no cuenta: la baja del sábado no es un día perdido")
    void ausenciaEnDiaNoLaborable() {
        assertThat(clase(sinCuadrante(SABADO), AbsenceType.MEDICO, false, false)).isEqualTo(Clase.NO_LABORABLE);
    }

    @Test
    @DisplayName("Las vacaciones salen de la cuenta, aunque ese día se fichara")
    void vacaciones() {
        assertThat(clase(sinCuadrante(LUNES), AbsenceType.VACACIONES, false, false)).isEqualTo(Clase.VACACIONES);
        assertThat(clase(sinCuadrante(LUNES), AbsenceType.VACACIONES, true, false)).isEqualTo(Clase.VACACIONES);
        assertThat(Clase.VACACIONES.cuenta()).isFalse();
    }

    @Test
    @DisplayName("El fichaje manda sobre una ausencia que no sean vacaciones, y el viaje de trabajo es trabajar")
    void fichadoYViaje() {
        assertThat(clase(sinCuadrante(LUNES), AbsenceType.MEDICO, true, false)).isEqualTo(Clase.TRABAJADO);
        assertThat(clase(sinCuadrante(LUNES), AbsenceType.VIAJE_TRABAJO, false, false)).isEqualTo(Clase.TRABAJADO);
    }

    @Test
    @DisplayName("Una ausencia aprobada sin fichaje es un día perdido con su motivo")
    void ausenciaJustificada() {
        ReglasDeAbsentismo.Dia dia =
                ReglasDeAbsentismo.clasificar(sinCuadrante(LUNES), AbsenceType.MEDICO, false, false);
        assertThat(dia.clase()).isEqualTo(Clase.AUSENCIA_JUSTIFICADA);
        assertThat(dia.motivo()).isEqualTo("MEDICO");
    }

    @Test
    @DisplayName("Una ausencia de cuadrante aceptada tiene motivo; sin nada más, es un día sin fichaje ni ausencia")
    void incidenciaAceptada() {
        ReglasDeAbsentismo.Dia dia = ReglasDeAbsentismo.clasificar(cuadrante(LUNES, 480), null, false, true);
        assertThat(dia.clase()).isEqualTo(Clase.AUSENCIA_JUSTIFICADA);
        assertThat(dia.motivo()).isEqualTo(ReglasDeAbsentismo.INCIDENCIA_ACEPTADA);
        assertThat(clase(cuadrante(LUNES, 480), null, false, false)).isEqualTo(Clase.SIN_FICHAJE);
    }

    @Test
    @DisplayName("El porcentaje lleva un decimal, redondea hacia arriba en el medio y es null sin denominador")
    void porcentaje() {
        assertThat(ReglasDeAbsentismo.porcentaje(1, 3)).isEqualByComparingTo(new BigDecimal("33.3"));
        assertThat(ReglasDeAbsentismo.porcentaje(1, 8)).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(ReglasDeAbsentismo.porcentaje(1, 16)).isEqualByComparingTo(new BigDecimal("6.3"));
        assertThat(ReglasDeAbsentismo.porcentaje(0, 0)).isNull();
    }

    @Test
    @DisplayName("Cada motivo tiene su etiqueta para leer")
    void etiquetas() {
        assertThat(ReglasDeAbsentismo.etiqueta("MEDICO")).isEqualTo("Consulta médica");
        assertThat(ReglasDeAbsentismo.etiqueta(ReglasDeAbsentismo.INCIDENCIA_ACEPTADA)).contains("aceptada");
    }
}
