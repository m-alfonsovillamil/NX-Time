package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.WorkStatus;
import com.nxtime.nxtime.dto.CompanyDashboardResponse;
import com.nxtime.nxtime.dto.PersonalDashboardResponse;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.VacationBalanceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unitarios del dashboard (Fase 10). Lo que se prueba aquí es la lógica
 * que vive en Java: los límites temporales que se le pasan al SQL, la
 * derivación del estado actual y la conversión a minutos. Que el
 * GROUP BY sume bien se prueba contra PostgreSQL real en
 * {@code DashboardServiceIT}.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private TimeEntryRepository timeEntryRepository;
    @Mock
    private AbsenceRequestRepository absenceRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VacationBalanceService vacationBalanceService;

    private DashboardServiceImpl service;

    private Company empresa;
    private User empleado;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(
                timeEntryRepository, absenceRequestRepository, userRepository, vacationBalanceService);
        empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        empleado = User.builder().id(10L).email("empleado@nxtime.test").nombre("Empleado")
                .empresa(empresa).activo(true).build();

        lenient().when(vacationBalanceService.getBalance(any(), anyInt()))
                .thenReturn(new VacationBalanceResponse(2026, 22, 0, 22));
    }

    private void conUsuario() {
        when(userRepository.findByEmail(empleado.getEmail())).thenReturn(Optional.of(empleado));
    }

    // ---- Panel personal ----

    @Test
    @DisplayName("Sin jornada abierta, el estado actual es SIN_JORNADA")
    void getPersonalDashboard_sinJornadaAbierta_estadoSinJornada() {
        conUsuario();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());

        assertThat(service.getPersonalDashboard(empleado.getEmail()).estadoActual())
                .isEqualTo(WorkStatus.SIN_JORNADA);
    }

    @Test
    @DisplayName("La jornada semanal esperada viaja en minutos, no en horas decimales")
    void getPersonalDashboard_devuelveLaJornadaSemanalEnMinutos() {
        conUsuario();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());

        // 40 h por defecto (art. 34 ET) = 2400 minutos.
        assertThat(service.getPersonalDashboard(empleado.getEmail()).minutosJornadaSemanal())
                .isEqualTo(2400L);
    }

    /**
     * 37,5 h es una jornada real y frecuente, y es justo el caso que
     * rompería si la conversión pasara por `double`: 37.5 * 60 en coma
     * flotante puede dar 2249.9999999999995, que truncado son 2249.
     */
    @Test
    @DisplayName("Una jornada con media hora no pierde un minuto por la coma flotante")
    void getPersonalDashboard_jornadaConMediaHora_noPierdeMinutos() {
        empleado.setHorasSemanales(new BigDecimal("37.5"));
        conUsuario();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());

        assertThat(service.getPersonalDashboard(empleado.getEmail()).minutosJornadaSemanal())
                .isEqualTo(2250L);
    }

    @Test
    @DisplayName("Con jornada abierta y sin pausa, el estado actual es TRABAJANDO")
    void getPersonalDashboard_jornadaAbierta_estadoTrabajando() {
        conUsuario();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(
                Optional.of(TimeEntry.builder().id(1L).horaEntrada(Instant.now()).enPausa(false).build()));

        assertThat(service.getPersonalDashboard(empleado.getEmail()).estadoActual())
                .isEqualTo(WorkStatus.TRABAJANDO);
    }

    @Test
    @DisplayName("Con jornada abierta y en pausa, el estado actual es EN_PAUSA")
    void getPersonalDashboard_jornadaEnPausa_estadoEnPausa() {
        conUsuario();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(
                Optional.of(TimeEntry.builder().id(1L).horaEntrada(Instant.now()).enPausa(true).build()));

        assertThat(service.getPersonalDashboard(empleado.getEmail()).estadoActual())
                .isEqualTo(WorkStatus.EN_PAUSA);
    }

    @Test
    @DisplayName("Los segundos del repositorio se exponen en minutos, truncando (89 s -> 1 min)")
    void getPersonalDashboard_segundosSeConviertenAMinutosTruncando() {
        conUsuario();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());
        when(timeEntryRepository.sumarSegundosTrabajados(anyLong(), any(), any())).thenReturn(89L);

        PersonalDashboardResponse resumen = service.getPersonalDashboard(empleado.getEmail());

        assertThat(resumen.minutosHoy()).isEqualTo(1);
    }

    @Test
    @DisplayName("Los tres periodos (hoy/semana/mes) empiezan en o antes de hoy y comparten fin")
    void getPersonalDashboard_periodosOrdenadosYConMismoFin() {
        conUsuario();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());

        service.getPersonalDashboard(empleado.getEmail());

        ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> hasta = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(timeEntryRepository, org.mockito.Mockito.times(3))
                .sumarSegundosTrabajados(eq(empleado.getId()), desde.capture(), hasta.capture());

        List<Instant> inicios = desde.getAllValues();
        // Orden de llamada: hoy, semana, mes. "Hoy" es el más reciente de
        // los tres -- eso sí se cumple siempre, sea cual sea la fecha.
        assertThat(inicios.get(0)).isAfterOrEqualTo(inicios.get(1));
        assertThat(inicios.get(0)).isAfterOrEqualTo(inicios.get(2));
        // Entre semana y mes NO hay orden garantizado, y por eso aquí no
        // se comprueba ninguno: cuando la semana en curso arrancó en el
        // mes anterior, su inicio es ANTERIOR al del mes. Pasa los
        // primeros días de casi todos los meses -- el miércoles 2/9/2026,
        // por ejemplo, la semana empieza el lunes 31/8 y el mes el 1/9.
        // No es un defecto: "esta semana" son los días desde el lunes,
        // aunque alguno caiga en agosto, así que los minutos de la semana
        // pueden superar a los del mes. Afirmar lo contrario hacía que
        // este test fallara según el día en que se ejecutara.
        // El fin es el mismo para los tres: mañana a las 00:00.
        assertThat(hasta.getAllValues()).containsOnly(hasta.getAllValues().get(0));
        assertThat(hasta.getAllValues().get(0)).isAfter(inicios.get(0));
    }

    @Test
    @DisplayName("El panel personal incluye las ausencias pendientes y el saldo de vacaciones")
    void getPersonalDashboard_incluyePendientesYSaldo() {
        conUsuario();
        when(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).thenReturn(Optional.empty());
        when(absenceRequestRepository.countByUsuarioAndEstado(empleado, AbsenceStatus.PENDIENTE)).thenReturn(3L);
        VacationBalanceResponse saldo = new VacationBalanceResponse(2026, 22, 5, 17);
        when(vacationBalanceService.getBalance(eq(empleado), anyInt())).thenReturn(saldo);

        PersonalDashboardResponse resumen = service.getPersonalDashboard(empleado.getEmail());

        assertThat(resumen.ausenciasPendientes()).isEqualTo(3);
        assertThat(resumen.saldoVacaciones()).isEqualTo(saldo);
    }

    @Test
    @DisplayName("Un usuario inexistente lanza ResourceNotFoundException")
    void getPersonalDashboard_usuarioInexistente_lanzaResourceNotFound() {
        when(userRepository.findByEmail("fantasma@nxtime.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPersonalDashboard("fantasma@nxtime.test"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- Panel de empresa ----

    @Test
    @DisplayName("El panel de empresa solo cuenta como activos a los empleados dados de alta")
    void getCompanyDashboard_soloCuentaEmpleadosActivos() {
        User gestor = User.builder().id(20L).email("gestor@nxtime.test").empresa(empresa).build();
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        when(userRepository.findByEmpresaAndRol(empresa, Role.EMPLEADO)).thenReturn(List.of(
                User.builder().id(1L).activo(true).build(),
                User.builder().id(2L).activo(true).build(),
                User.builder().id(3L).activo(false).build())); // dado de baja
        when(timeEntryRepository.sumarSegundosPorEmpleado(anyLong(), any(), any())).thenReturn(List.of());

        CompanyDashboardResponse resumen = service.getCompanyDashboard(gestor.getEmail());

        assertThat(resumen.empleadosActivos()).isEqualTo(2);
    }

    @Test
    @DisplayName("El panel de empresa expone las incidencias de fichaje sin corregir")
    void getCompanyDashboard_exponeIncidenciasAbiertas() {
        User gestor = User.builder().id(20L).email("gestor@nxtime.test").empresa(empresa).build();
        when(userRepository.findByEmail(gestor.getEmail())).thenReturn(Optional.of(gestor));
        when(userRepository.findByEmpresaAndRol(empresa, Role.EMPLEADO)).thenReturn(List.of());
        when(timeEntryRepository.sumarSegundosPorEmpleado(anyLong(), any(), any())).thenReturn(List.of());
        when(timeEntryRepository.contarIncidenciasAbiertas(empresa)).thenReturn(4L);
        when(absenceRequestRepository.countByEmpresa_IdAndEstado(empresa.getId(), AbsenceStatus.PENDIENTE))
                .thenReturn(2L);

        CompanyDashboardResponse resumen = service.getCompanyDashboard(gestor.getEmail());

        assertThat(resumen.incidenciasAbiertas()).isEqualTo(4);
        assertThat(resumen.ausenciasPendientes()).isEqualTo(2);
    }
}
