package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CompanyDashboardResponse;
import com.nxtime.nxtime.dto.EmployeeHoursDTO;
import com.nxtime.nxtime.dto.PersonalDashboardResponse;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.DashboardService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Test de INTEGRACIÓN del dashboard (Fase 10) contra PostgreSQL real.
 *
 * Existe porque los agregados son **SQL nativo**
 * (EXTRACT(EPOCH FROM ...), GROUP BY): con mocks no se comprueba nada
 * de lo que puede salir mal ahí -- que la resta de instantes dé
 * segundos, que se descuenten las pausas, que las jornadas abiertas o
 * anuladas no cuenten, y que el GROUP BY agrupe por empleado. Todo eso
 * solo existe en la base de datos.
 *
 * Requisito: `docker compose up -d postgres` (ver ApiContractTest).
 */
@SpringBootTest
class DashboardServiceIT {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "dashboard_it_" + System.nanoTime();
        String adminUrl = "jdbc:postgresql://localhost:5433/nxtime";
        try (Connection admin = DriverManager.getConnection(adminUrl, "nxtime", "nxtime");
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + testDb);
        }

        String testUrl = "jdbc:postgresql://localhost:5433/" + testDb;
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> "nxtime_app");
        registry.add("spring.datasource.password", () -> "nxtime_app");
        registry.add("spring.flyway.url", () -> testUrl);
        registry.add("spring.flyway.user", () -> "nxtime");
        registry.add("spring.flyway.password", () -> "nxtime");
    }

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    private Company crearEmpresa(String nombre) {
        return companyRepository.save(Company.builder().nombre(nombre).build());
    }

    private User crearUsuario(Company empresa, String email, Role rol) {
        return userRepository.save(User.builder()
                .email(email).nombre("Usuario " + email).contrasena("hash")
                .rol(rol).empresa(empresa).activo(true).build());
    }

    /** Jornada de HOY (hora española) de las 9:00 a la hora indicada, con las pausas dadas. */
    private TimeEntry jornadaDeHoy(User usuario, int horaSalida, long segundosPausa) {
        LocalDate hoy = LocalDate.now(MADRID);
        return timeEntryRepository.save(TimeEntry.builder()
                .usuario(usuario)
                .empresa(usuario.getEmpresa())
                .horaEntrada(ZonedDateTime.of(hoy, LocalTime.of(9, 0), MADRID).toInstant())
                .horaSalida(ZonedDateTime.of(hoy, LocalTime.of(horaSalida, 0), MADRID).toInstant())
                .segundosPausaAcumulados(segundosPausa)
                .build());
    }

    @Test
    @DisplayName("El SQL agregado resta las pausas: 9:00-17:00 con 30 min de pausa son 450 minutos")
    void panelPersonal_descuentaLasPausas() {
        Company empresa = crearEmpresa("Pausas SL");
        User empleado = crearUsuario(empresa, "pausas@nxtime.test", Role.EMPLEADO);
        jornadaDeHoy(empleado, 17, 30 * 60L); // 8 h brutas - 30 min = 7 h 30 min

        PersonalDashboardResponse resumen = dashboardService.getPersonalDashboard(empleado.getEmail());

        assertThat(resumen.minutosHoy()).isEqualTo(450);
    }

    @Test
    @DisplayName("Una jornada todavía abierta no suma horas (aún no ha producido ninguna)")
    void panelPersonal_jornadaAbierta_noSuma() {
        Company empresa = crearEmpresa("Abierta SL");
        User empleado = crearUsuario(empresa, "abierta@nxtime.test", Role.EMPLEADO);
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(empleado).empresa(empresa)
                .horaEntrada(Instant.now().minusSeconds(3600))
                .build());

        PersonalDashboardResponse resumen = dashboardService.getPersonalDashboard(empleado.getEmail());

        assertThat(resumen.minutosHoy()).isZero();
        // Pero sí cuenta para el estado actual.
        assertThat(resumen.estadoActual().name()).isEqualTo("TRABAJANDO");
    }

    @Test
    @DisplayName("Una jornada anulada por una corrección no suma horas (Fase 8)")
    void panelPersonal_jornadaAnulada_noSuma() {
        Company empresa = crearEmpresa("Anulada SL");
        User empleado = crearUsuario(empresa, "anulada@nxtime.test", Role.EMPLEADO);
        TimeEntry anulada = jornadaDeHoy(empleado, 17, 0);
        anulada.setAnulado(true);
        timeEntryRepository.save(anulada);

        PersonalDashboardResponse resumen = dashboardService.getPersonalDashboard(empleado.getEmail());

        assertThat(resumen.minutosHoy()).isZero();
    }

    @Test
    @DisplayName("Una jornada de hace dos meses no cuenta en 'hoy' pero el usuario sigue existiendo")
    void panelPersonal_jornadaAntigua_noCuentaEnHoy() {
        Company empresa = crearEmpresa("Antigua SL");
        User empleado = crearUsuario(empresa, "antigua@nxtime.test", Role.EMPLEADO);
        LocalDate haceDosMeses = LocalDate.now(MADRID).minusMonths(2);
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(empleado).empresa(empresa)
                .horaEntrada(ZonedDateTime.of(haceDosMeses, LocalTime.of(9, 0), MADRID).toInstant())
                .horaSalida(ZonedDateTime.of(haceDosMeses, LocalTime.of(17, 0), MADRID).toInstant())
                .build());

        PersonalDashboardResponse resumen = dashboardService.getPersonalDashboard(empleado.getEmail());

        assertThat(resumen.minutosHoy()).isZero();
        assertThat(resumen.minutosMes()).isZero();
    }

    @Test
    @DisplayName("El GROUP BY agrupa por empleado y ordena de más a menos horas")
    void panelEmpresa_agrupaYOrdenaPorEmpleado() {
        Company empresa = crearEmpresa("Equipo SL");
        User gestor = crearUsuario(empresa, "gestor.dash@nxtime.test", Role.GESTOR);
        User madruga = crearUsuario(empresa, "madruga@nxtime.test", Role.EMPLEADO);
        User temprano = crearUsuario(empresa, "temprano@nxtime.test", Role.EMPLEADO);

        jornadaDeHoy(madruga, 18, 0);   // 9 h
        jornadaDeHoy(temprano, 14, 0);  // 5 h

        CompanyDashboardResponse resumen = dashboardService.getCompanyDashboard(gestor.getEmail());

        assertThat(resumen.empleadosActivos()).isEqualTo(2);
        assertThat(resumen.horasPorEmpleado()).hasSize(2);
        assertThat(resumen.horasPorEmpleado()).extracting(EmployeeHoursDTO::nombre)
                .containsExactly(madruga.getNombre(), temprano.getNombre()); // de más a menos
        assertThat(resumen.horasPorEmpleado().get(0).minutos()).isEqualTo(9 * 60);
        assertThat(resumen.horasPorEmpleado().get(1).minutos()).isEqualTo(5 * 60);
        assertThat(resumen.minutosMesEmpresa()).isEqualTo(14 * 60);
    }

    @Test
    @DisplayName("El panel de una empresa no incluye horas de otra (aislamiento multi-tenant)")
    void panelEmpresa_noMezclaEmpresas() {
        Company mia = crearEmpresa("Mia SL");
        Company ajena = crearEmpresa("Ajena SL");
        User gestorMio = crearUsuario(mia, "gestor.mio@nxtime.test", Role.GESTOR);
        User empleadoMio = crearUsuario(mia, "empleado.mio@nxtime.test", Role.EMPLEADO);
        User empleadoAjeno = crearUsuario(ajena, "empleado.ajeno@nxtime.test", Role.EMPLEADO);

        jornadaDeHoy(empleadoMio, 13, 0);    // 4 h
        jornadaDeHoy(empleadoAjeno, 20, 0);  // 11 h, de otra empresa

        CompanyDashboardResponse resumen = dashboardService.getCompanyDashboard(gestorMio.getEmail());

        assertThat(resumen.minutosMesEmpresa()).isEqualTo(4 * 60);
        assertThat(resumen.horasPorEmpleado()).extracting(EmployeeHoursDTO::nombre)
                .containsExactly(empleadoMio.getNombre());
    }

    @Test
    @DisplayName("Las jornadas cerradas por el sistema cuentan como incidencias abiertas (Fase 9)")
    void panelEmpresa_cuentaIncidenciasAbiertas() {
        Company empresa = crearEmpresa("Incidencias SL");
        User gestor = crearUsuario(empresa, "gestor.inc@nxtime.test", Role.GESTOR);
        User empleado = crearUsuario(empresa, "empleado.inc@nxtime.test", Role.EMPLEADO);

        TimeEntry incidencia = jornadaDeHoy(empleado, 17, 0);
        incidencia.setJornadaIncompleta(true);
        timeEntryRepository.save(incidencia);

        CompanyDashboardResponse resumen = dashboardService.getCompanyDashboard(gestor.getEmail());

        assertThat(resumen.incidenciasAbiertas()).isEqualTo(1);
    }
}
