package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.report.MonthlyReport;
import com.nxtime.nxtime.report.ReportRow;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
 * Unitarios de la preparación de datos de los informes.
 *
 * Esta clase estaba al **14 % de cobertura** y por ahí se coló un bug
 * real: el total mensual perdía hasta 21 minutos porque las filas se
 * truncaban a minutos antes de sumarse (ver MonthlyReportTotalTest). Los
 * tests que había probaban los generadores con datos escritos a mano y
 * el controlador con el servicio simulado, así que **el cálculo de
 * verdad no lo comprobaba nadie**.
 *
 * Lo que se prueba aquí es justo eso: la conversión de fichaje a línea
 * de informe, la proyección a hora española, la resta de pausas, los
 * límites del mes y el control de empresa.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Mock
    private TimeEntryRepository timeEntryRepository;
    @Mock
    private UserRepository userRepository;

    private ReportServiceImpl service;

    private Company empresa;
    private User solicitante;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(timeEntryRepository, userRepository);
        empresa = Company.builder().id(1L).nombre("TechCorp").build();
        solicitante = User.builder().id(10L).email("rrhh@nxtime.test").nombre("RRHH")
                .rol(Role.RRHH).empresa(empresa).activo(true).build();
    }

    /** Jornada del día indicado de junio de 2026, en hora española. */
    private TimeEntry jornada(LocalTime entrada, LocalTime salida, long segundosPausa) {
        LocalDate dia = LocalDate.of(2026, 6, 1);
        User empleado = User.builder().id(20L).nombre("Ana").empresa(empresa).build();
        return TimeEntry.builder()
                .id(100L).usuario(empleado).empresa(empresa)
                .horaEntrada(ZonedDateTime.of(dia, entrada, MADRID).toInstant())
                .horaSalida(ZonedDateTime.of(dia, salida, MADRID).toInstant())
                .segundosPausaAcumulados(segundosPausa)
                .build();
    }

    // ---- Cálculo de la fila ----

    @Test
    @DisplayName("La fila guarda SEGUNDOS netos, no minutos: es lo que evita perder tiempo al sumar")
    void aFila_guardaSegundosNetosSinTruncar() {
        when(userRepository.findByEmail(solicitante.getEmail())).thenReturn(Optional.of(solicitante));
        // 9:00 a 17:00 = 8 h; menos 40 s de pausa = 28760 s (no múltiplo de 60).
        when(timeEntryRepository.findParaInforme(any(), any(), any()))
                .thenReturn(List.of(jornada(LocalTime.of(9, 0), LocalTime.of(17, 0), 40)));

        MonthlyReport informe = service.informeDeEmpresa(solicitante.getEmail(), YearMonth.of(2026, 6));

        ReportRow fila = informe.filas().get(0);
        assertThat(fila.segundosNetos()).isEqualTo(8 * 3600 - 40);
        // Al mostrarla sí se trunca, pero el dato exacto se conserva.
        assertThat(fila.minutosNetos()).isEqualTo(479);
        assertThat(informe.totalSegundos()).isEqualTo(8 * 3600 - 40);
    }

    @Test
    @DisplayName("Se descuentan las pausas del tiempo efectivo")
    void aFila_descuentaLasPausas() {
        when(userRepository.findByEmail(solicitante.getEmail())).thenReturn(Optional.of(solicitante));
        when(timeEntryRepository.findParaInforme(any(), any(), any()))
                .thenReturn(List.of(jornada(LocalTime.of(9, 0), LocalTime.of(17, 30), 30 * 60)));

        ReportRow fila = service.informeDeEmpresa(solicitante.getEmail(), YearMonth.of(2026, 6)).filas().get(0);

        assertThat(fila.minutosPausa()).isEqualTo(30);
        assertThat(fila.segundosNetos()).isEqualTo((8L * 3600) + (30 * 60) - (30 * 60)); // 8 h netas
        assertThat(fila.duracionLegible()).isEqualTo("8h 00m");
    }

    @Test
    @DisplayName("Las horas se muestran en hora ESPAÑOLA, no en UTC")
    void aFila_proyectaAHoraEspanola() {
        when(userRepository.findByEmail(solicitante.getEmail())).thenReturn(Optional.of(solicitante));
        // En junio España va a UTC+2: las 9:00 locales son las 7:00 UTC.
        when(timeEntryRepository.findParaInforme(any(), any(), any()))
                .thenReturn(List.of(jornada(LocalTime.of(9, 0), LocalTime.of(17, 0), 0)));

        ReportRow fila = service.informeDeEmpresa(solicitante.getEmail(), YearMonth.of(2026, 6)).filas().get(0);

        assertThat(fila.horaEntrada()).isEqualTo(LocalTime.of(9, 0));
        assertThat(fila.horaSalida()).isEqualTo(LocalTime.of(17, 0));
        assertThat(fila.fecha()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("Una jornada cerrada por el sistema se marca como incidencia")
    void aFila_marcaLasIncidencias() {
        when(userRepository.findByEmail(solicitante.getEmail())).thenReturn(Optional.of(solicitante));
        TimeEntry incompleta = jornada(LocalTime.of(9, 0), LocalTime.of(17, 0), 0);
        incompleta.setJornadaIncompleta(true);
        when(timeEntryRepository.findParaInforme(any(), any(), any())).thenReturn(List.of(incompleta));

        MonthlyReport informe = service.informeDeEmpresa(solicitante.getEmail(), YearMonth.of(2026, 6));

        assertThat(informe.filas().get(0).incidencia()).isTrue();
        assertThat(informe.incidencias()).isEqualTo(1);
    }

    // ---- Límites del periodo ----

    @Test
    @DisplayName("El rango va del día 1 del mes al día 1 del siguiente, en hora española")
    void informe_acotaElMesCorrectamente() {
        when(userRepository.findByEmail(solicitante.getEmail())).thenReturn(Optional.of(solicitante));
        when(timeEntryRepository.findParaInforme(any(), any(), any())).thenReturn(List.of());

        service.informeDeEmpresa(solicitante.getEmail(), YearMonth.of(2026, 6));

        ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> hasta = ArgumentCaptor.forClass(Instant.class);
        verify(timeEntryRepository).findParaInforme(eq(empresa), desde.capture(), hasta.capture());

        assertThat(desde.getValue())
                .isEqualTo(LocalDate.of(2026, 6, 1).atStartOfDay(MADRID).toInstant());
        // Fin EXCLUSIVO: el 1 de julio a las 00:00, no el 30 de junio.
        assertThat(hasta.getValue())
                .isEqualTo(LocalDate.of(2026, 7, 1).atStartOfDay(MADRID).toInstant());
    }

    // ---- Informe individual y aislamiento multi-tenant ----

    @Test
    @DisplayName("El informe individual lleva el nombre del empleado, no 'todos'")
    void informeDeEmpleado_llevaElNombreDelEmpleado() {
        User empleado = User.builder().id(20L).nombre("Ana Fernández").empresa(empresa).build();
        when(userRepository.findByEmail(solicitante.getEmail())).thenReturn(Optional.of(solicitante));
        when(userRepository.findById(20L)).thenReturn(Optional.of(empleado));
        when(timeEntryRepository.findParaInformeDeEmpleado(any(), any(), any())).thenReturn(List.of());

        MonthlyReport informe = service.informeDeEmpleado(solicitante.getEmail(), 20L, YearMonth.of(2026, 6));

        assertThat(informe.nombreEmpleado()).isEqualTo("Ana Fernández");
        assertThat(informe.nombreEmpresa()).isEqualTo("TechCorp");
    }

    @Test
    @DisplayName("Generar el informe de un empleado de OTRA empresa lanza TenantAccessException")
    void informeDeEmpleado_deOtraEmpresa_lanzaTenantAccessException() {
        Company otraEmpresa = Company.builder().id(2L).nombre("Ajena SL").build();
        User ajeno = User.builder().id(30L).nombre("Ajeno").empresa(otraEmpresa).build();
        when(userRepository.findByEmail(solicitante.getEmail())).thenReturn(Optional.of(solicitante));
        when(userRepository.findById(30L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.informeDeEmpleado(solicitante.getEmail(), 30L, YearMonth.of(2026, 6)))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("Un empleado inexistente lanza ResourceNotFoundException")
    void informeDeEmpleado_inexistente_lanzaResourceNotFound() {
        when(userRepository.findByEmail(solicitante.getEmail())).thenReturn(Optional.of(solicitante));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.informeDeEmpleado(solicitante.getEmail(), 99L, YearMonth.of(2026, 6)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Un solicitante inexistente lanza ResourceNotFoundException")
    void informeDeEmpresa_solicitanteInexistente_lanzaResourceNotFound() {
        when(userRepository.findByEmail("fantasma@nxtime.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.informeDeEmpresa("fantasma@nxtime.test", YearMonth.of(2026, 6)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- El total, que es donde estaba el bug ----

    @Test
    @DisplayName("Varias jornadas con segundos sueltos no pierden minutos en el total")
    void informe_totalNoPierdeMinutos() {
        when(userRepository.findByEmail(solicitante.getEmail())).thenReturn(Optional.of(solicitante));
        // Tres jornadas de 7h59m40s: 3 x 28780 s = 86340 s = 23 h 59 min.
        // Truncando por fila darían 23 h 57 min.
        TimeEntry j = jornada(LocalTime.of(9, 0), LocalTime.of(17, 0), 20);
        when(timeEntryRepository.findParaInforme(any(), any(), any())).thenReturn(List.of(j, j, j));

        MonthlyReport informe = service.informeDeEmpresa(solicitante.getEmail(), YearMonth.of(2026, 6));

        long segundosPorJornada = 8L * 3600 - 20;
        assertThat(informe.totalSegundos()).isEqualTo(3 * segundosPorJornada);
        assertThat(informe.totalMinutos()).isEqualTo((3 * segundosPorJornada) / 60);
    }
}
