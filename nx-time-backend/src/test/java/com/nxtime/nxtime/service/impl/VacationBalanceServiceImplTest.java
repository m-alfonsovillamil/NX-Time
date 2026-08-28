package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.VacationBalance;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.VacationBalanceRepository;
import com.nxtime.nxtime.service.WorkingDayService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unitarios del saldo de vacaciones (Fase 9). Lo que se prueba aquí,
 * sobre todo, es que los días consumidos se DERIVAN de las peticiones
 * aprobadas y no de un contador guardado (ver VacationBalance).
 */
@ExtendWith(MockitoExtension.class)
class VacationBalanceServiceImplTest {

    @Mock
    private VacationBalanceRepository vacationBalanceRepository;
    @Mock
    private AbsenceRequestRepository absenceRequestRepository;
    @Mock
    private WorkingDayService workingDayService;

    private VacationBalanceServiceImpl service;

    private User empleado;

    @BeforeEach
    void setUp() {
        service = new VacationBalanceServiceImpl(
                vacationBalanceRepository, absenceRequestRepository, workingDayService);
        Company empresa = Company.builder().id(1L).build();
        empleado = User.builder().id(10L).email("empleado@nxtime.test").empresa(empresa).build();
    }

    @Test
    @DisplayName("Sin fila propia de saldo, se usa el derecho por defecto (22 días, mínimo legal)")
    void getBalance_sinFilaPropia_usaElDerechoPorDefecto() {
        when(vacationBalanceRepository.findByUsuarioAndAnio(empleado, 2026)).thenReturn(Optional.empty());
        when(absenceRequestRepository.findVacacionesAprobadasDelAnio(any(), any(), any())).thenReturn(List.of());

        VacationBalanceResponse saldo = service.getBalance(empleado, 2026);

        assertThat(saldo.diasTotales()).isEqualTo(VacationBalanceServiceImpl.DIAS_POR_DEFECTO);
        assertThat(saldo.diasConsumidos()).isZero();
        assertThat(saldo.diasDisponibles()).isEqualTo(VacationBalanceServiceImpl.DIAS_POR_DEFECTO);
    }

    @Test
    @DisplayName("Con fila propia, manda el derecho guardado y no el valor por defecto")
    void getBalance_conFilaPropia_usaElDerechoGuardado() {
        when(vacationBalanceRepository.findByUsuarioAndAnio(empleado, 2026))
                .thenReturn(Optional.of(VacationBalance.builder().usuario(empleado).anio(2026).diasTotales(30).build()));
        when(absenceRequestRepository.findVacacionesAprobadasDelAnio(any(), any(), any())).thenReturn(List.of());

        assertThat(service.getBalance(empleado, 2026).diasTotales()).isEqualTo(30);
    }

    @Test
    @DisplayName("Los días consumidos se suman en días HÁBILES de las peticiones aprobadas, no en días naturales")
    void getBalance_consumidosSeCuentanEnDiasHabiles() {
        when(vacationBalanceRepository.findByUsuarioAndAnio(empleado, 2026)).thenReturn(Optional.empty());
        AbsenceRequest aprobada1 = AbsenceRequest.builder().id(1L)
                .fechaInicio(LocalDate.of(2026, 6, 1)).fechaFin(LocalDate.of(2026, 6, 7)).build();
        AbsenceRequest aprobada2 = AbsenceRequest.builder().id(2L)
                .fechaInicio(LocalDate.of(2026, 8, 3)).fechaFin(LocalDate.of(2026, 8, 7)).build();
        when(absenceRequestRepository.findVacacionesAprobadasDelAnio(any(), any(), any()))
                .thenReturn(List.of(aprobada1, aprobada2));
        // 7 días naturales -> 5 hábiles; 5 días naturales -> 5 hábiles.
        when(workingDayService.contarDiasHabiles(any(), eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 7))))
                .thenReturn(5);
        when(workingDayService.contarDiasHabiles(any(), eq(LocalDate.of(2026, 8, 3)), eq(LocalDate.of(2026, 8, 7))))
                .thenReturn(5);

        VacationBalanceResponse saldo = service.getBalance(empleado, 2026);

        assertThat(saldo.diasConsumidos()).isEqualTo(10);
        assertThat(saldo.diasDisponibles()).isEqualTo(VacationBalanceServiceImpl.DIAS_POR_DEFECTO - 10);
    }

    @Test
    @DisplayName("Una petición a caballo entre dos años solo consume los días que caen dentro del año consultado")
    void getBalance_peticionEntreDosAnios_recortaAlAnioConsultado() {
        when(vacationBalanceRepository.findByUsuarioAndAnio(empleado, 2026)).thenReturn(Optional.empty());
        // Del 28/12/2025 al 4/1/2026: al consultar 2026 solo cuentan los
        // días a partir del 1 de enero.
        AbsenceRequest aCaballo = AbsenceRequest.builder().id(1L)
                .fechaInicio(LocalDate.of(2025, 12, 28)).fechaFin(LocalDate.of(2026, 1, 4)).build();
        when(absenceRequestRepository.findVacacionesAprobadasDelAnio(any(), any(), any())).thenReturn(List.of(aCaballo));
        when(workingDayService.contarDiasHabiles(any(), any(), any())).thenReturn(2);

        service.getBalance(empleado, 2026);

        // El rango que llega al cálculo empieza el 1 de enero de 2026,
        // no el 28 de diciembre de 2025.
        verify(workingDayService).contarDiasHabiles(
                any(), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 1, 4)));
    }

    @Test
    @DisplayName("diasDisponibles es siempre diasTotales - diasConsumidos")
    void getBalance_disponiblesEsLaResta() {
        when(vacationBalanceRepository.findByUsuarioAndAnio(eq(empleado), anyInt()))
                .thenReturn(Optional.of(VacationBalance.builder().anio(2026).diasTotales(25).build()));
        AbsenceRequest aprobada = AbsenceRequest.builder().id(1L)
                .fechaInicio(LocalDate.of(2026, 6, 1)).fechaFin(LocalDate.of(2026, 6, 5)).build();
        when(absenceRequestRepository.findVacacionesAprobadasDelAnio(any(), any(), any())).thenReturn(List.of(aprobada));
        when(workingDayService.contarDiasHabiles(any(), any(), any())).thenReturn(5);

        VacationBalanceResponse saldo = service.getBalance(empleado, 2026);

        assertThat(saldo.diasDisponibles()).isEqualTo(saldo.diasTotales() - saldo.diasConsumidos()).isEqualTo(20);
    }
}
