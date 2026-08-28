package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.service.HolidayCalendar;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unitarios del cálculo de días hábiles (Fase 9). Sin esto, "5 días de
 * vacaciones" contaba también sábados, domingos y festivos.
 *
 * Las fechas concretas están elegidas a mano sobre el calendario de
 * 2026: el 1 de junio de 2026 es lunes, así que la semana del 1 al 7 es
 * lunes-domingo completa. Fijarlas evita que el test dependa de "hoy".
 */
@ExtendWith(MockitoExtension.class)
class WorkingDayServiceImplTest {

    @Mock
    private HolidayCalendar holidayCalendar;

    private WorkingDayServiceImpl service;
    private Company empresa;

    @BeforeEach
    void setUp() {
        service = new WorkingDayServiceImpl(holidayCalendar);
        empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        // Desde la Fase 10 los festivos vienen del calendario cacheado,
        // ya como Set<LocalDate> y por AÑO, no por rango de fechas.
        lenient().when(holidayCalendar.festivosDelAnio(anyLong(), anyInt())).thenReturn(Set.of());
    }

    private void conFestivos(LocalDate... fechas) {
        when(holidayCalendar.festivosDelAnio(anyLong(), anyInt())).thenReturn(Set.of(fechas));
    }

    @Test
    @DisplayName("Una semana natural completa (lunes a domingo) son 5 días hábiles")
    void contarDiasHabiles_semanaCompleta_devuelve5() {
        int habiles = service.contarDiasHabiles(empresa, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7));

        assertThat(habiles).isEqualTo(5);
    }

    @Test
    @DisplayName("Un único día laborable cuenta 1 (rango de un solo día, ambos extremos incluidos)")
    void contarDiasHabiles_unSoloDiaLaborable_devuelve1() {
        int habiles = service.contarDiasHabiles(empresa, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));

        assertThat(habiles).isEqualTo(1);
    }

    @Test
    @DisplayName("Un fin de semana entero son 0 días hábiles")
    void contarDiasHabiles_soloFinDeSemana_devuelve0() {
        // 6 y 7 de junio de 2026: sábado y domingo.
        int habiles = service.contarDiasHabiles(empresa, LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 7));

        assertThat(habiles).isZero();
    }

    @Test
    @DisplayName("Un festivo entre semana se descuenta de los días hábiles")
    void contarDiasHabiles_conFestivoEntreSemana_loDescuenta() {
        // Miércoles 3 de junio de 2026, festivo inventado de empresa.
        conFestivos(LocalDate.of(2026, 6, 3));

        int habiles = service.contarDiasHabiles(empresa, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7));

        assertThat(habiles).isEqualTo(4);
    }

    @Test
    @DisplayName("Un festivo que cae en sábado no descuenta dos veces")
    void contarDiasHabiles_festivoEnFinDeSemana_noDescuentaDosVeces() {
        conFestivos(LocalDate.of(2026, 6, 6));

        int habiles = service.contarDiasHabiles(empresa, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7));

        assertThat(habiles).isEqualTo(5);
    }

    @Test
    @DisplayName("Un rango invertido (desde posterior a hasta) devuelve 0, sin explotar")
    void contarDiasHabiles_rangoInvertido_devuelve0() {
        int habiles = service.contarDiasHabiles(empresa, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1));

        assertThat(habiles).isZero();
    }
}
