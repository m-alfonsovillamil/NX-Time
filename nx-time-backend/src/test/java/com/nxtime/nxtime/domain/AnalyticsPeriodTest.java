package com.nxtime.nxtime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalyticsPeriodTest {

    @Test
    @DisplayName("Mes, trimestre y año naturales que contienen la fecha, bisiestos incluidos")
    void limites() {
        LocalDate fecha = LocalDate.of(2028, 2, 15);
        assertThat(AnalyticsPeriod.MES.inicio(fecha)).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(AnalyticsPeriod.MES.fin(fecha)).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(AnalyticsPeriod.TRIMESTRE.inicio(fecha)).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(AnalyticsPeriod.TRIMESTRE.fin(fecha)).isEqualTo(LocalDate.of(2028, 3, 31));
        assertThat(AnalyticsPeriod.TRIMESTRE.inicio(LocalDate.of(2026, 11, 3))).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(AnalyticsPeriod.TRIMESTRE.fin(LocalDate.of(2026, 11, 3))).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(AnalyticsPeriod.ANIO.inicio(fecha)).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(AnalyticsPeriod.ANIO.fin(fecha)).isEqualTo(LocalDate.of(2028, 12, 31));
    }
}
