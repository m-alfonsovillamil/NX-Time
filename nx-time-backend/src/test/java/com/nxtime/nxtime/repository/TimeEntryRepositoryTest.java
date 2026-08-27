package com.nxtime.nxtime.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * {@code @DataJpaTest} de TimeEntryRepository contra un PostgreSQL real:
 * verifica el filtrado por empresa/rol de {@code findTeamHistory} (ver
 * auditoría, hueco de aislamiento multi-tenant que tenía la versión
 * original con {@code findByEmpresa}) y el orden/paginación de
 * {@code findHistoryByUsuario}. Este tipo de bug no lo detecta H2 --
 * las dos consultas usan JOIN FETCH y filtros específicos que solo se
 * validan de verdad contra el dialecto real.
 */
class TimeEntryRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    private Company empresa;
    private User empleado;
    private User gestor;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa Test").build());
        empleado = userRepository.save(User.builder()
                .email("empleado@nxtime.test").nombre("Empleado").contrasena("hash")
                .rol(Role.EMPLEADO).empresa(empresa).build());
        gestor = userRepository.save(User.builder()
                .email("gestor@nxtime.test").nombre("Gestor").contrasena("hash")
                .rol(Role.GESTOR).empresa(empresa).build());
    }

    @Test
    @DisplayName("findByUsuarioAndHoraSalidaIsNull encuentra la jornada abierta y solo esa")
    void findByUsuarioAndHoraSalidaIsNull_encuentraSoloLaJornadaAbierta() {
        timeEntryRepository.save(TimeEntry.builder().usuario(empleado).empresa(empresa)
                .horaEntrada(Instant.now().minusSeconds(7200)).horaSalida(Instant.now().minusSeconds(3600)).build());
        TimeEntry abierta = timeEntryRepository.save(TimeEntry.builder().usuario(empleado).empresa(empresa)
                .horaEntrada(Instant.now()).build());

        Optional<TimeEntry> result = timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(abierta.getId());
    }

    @Test
    @DisplayName("findHistoryByUsuario devuelve el historial del usuario ordenado por horaEntrada DESC")
    void findHistoryByUsuario_ordenaPorHoraEntradaDescendente() {
        Instant ahora = Instant.now();
        TimeEntry antiguo = timeEntryRepository.save(TimeEntry.builder().usuario(empleado).empresa(empresa)
                .horaEntrada(ahora.minusSeconds(7200)).horaSalida(ahora.minusSeconds(3600)).build());
        TimeEntry reciente = timeEntryRepository.save(TimeEntry.builder().usuario(empleado).empresa(empresa)
                .horaEntrada(ahora.minusSeconds(1800)).horaSalida(ahora.minusSeconds(900)).build());
        // De otro usuario: no debe aparecer en el historial del empleado.
        timeEntryRepository.save(TimeEntry.builder().usuario(gestor).empresa(empresa).horaEntrada(ahora).build());

        List<TimeEntry> historial = timeEntryRepository.findHistoryByUsuario(empleado, PageRequest.of(0, 200));

        assertThat(historial).extracting(TimeEntry::getId).containsExactly(reciente.getId(), antiguo.getId());
    }

    @Test
    @DisplayName("findTeamHistory solo devuelve fichajes de usuarios EMPLEADO de la empresa, no de otros gestores")
    void findTeamHistory_soloIncluyeEmpleadosDeLaEmpresa() {
        TimeEntry delEmpleado = timeEntryRepository.save(
                TimeEntry.builder().usuario(empleado).empresa(empresa).horaEntrada(Instant.now()).build());
        // El propio fichaje del gestor NO debe salir en "el historial de mi equipo".
        timeEntryRepository.save(
                TimeEntry.builder().usuario(gestor).empresa(empresa).horaEntrada(Instant.now()).build());

        Company otraEmpresa = companyRepository.save(Company.builder().nombre("Otra Empresa").build());
        User empleadoDeOtraEmpresa = userRepository.save(User.builder()
                .email("otro@nxtime.test").nombre("Otro").contrasena("hash")
                .rol(Role.EMPLEADO).empresa(otraEmpresa).build());
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(empleadoDeOtraEmpresa).empresa(otraEmpresa).horaEntrada(Instant.now()).build());

        List<TimeEntry> equipo = timeEntryRepository.findTeamHistory(empresa, PageRequest.of(0, 200));

        assertThat(equipo).extracting(TimeEntry::getId).containsExactly(delEmpleado.getId());
    }
}
