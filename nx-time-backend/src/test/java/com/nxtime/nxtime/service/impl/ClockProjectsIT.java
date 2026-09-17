package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.ProjectAllocation;
import com.nxtime.nxtime.domain.ProjectAssignment;
import com.nxtime.nxtime.domain.ProjectSegment;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ClockProjectsResponse;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.ProjectAllocationRepository;
import com.nxtime.nxtime.repository.ProjectAssignmentRepository;
import com.nxtime.nxtime.repository.ProjectRepository;
import com.nxtime.nxtime.repository.ProjectSegmentRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Elegir y cambiar de proyecto al fichar (ADR 017), contra PostgreSQL real.
 *
 * Las horas se mueven hacia atrás con SQL para que los tramos duren algo: el
 * servicio ficha con {@code Instant.now()} y un test no puede esperar horas.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class ClockProjectsIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "fichar_proyectos_it_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection("jdbc:postgresql://localhost:5433/nxtime", "nxtime", "nxtime");
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
    private TimeEntryService service;
    @Autowired
    private ProjectSegmentRepository segmentRepository;
    @Autowired
    private ProjectAllocationRepository allocationRepository;
    @Autowired
    private ProjectAssignmentRepository assignmentRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Company empresa;
    private User ana;
    private Project core;
    private Project app;
    private Project cerrado;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ana = persona("ana");
        core = proyecto("CORE", true);
        app = proyecto("APP", true);
        cerrado = proyecto("VIEJO", false);
    }

    private User persona(String nombre) {
        return userRepository.save(User.builder()
                .nombre(nombre).email(nombre + System.nanoTime() + "@test").contrasena("x")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).horasSemanales(new BigDecimal("40.0")).build());
    }

    private Project proyecto(String codigo, boolean activo) {
        return projectRepository.save(Project.builder()
                .empresa(empresa).codigo(codigo + System.nanoTime() % 100000).nombre(codigo)
                .fechaInicio(LocalDate.of(2026, 1, 1)).activo(activo).build());
    }

    private void asignar(User quien, Project proyecto) {
        assignmentRepository.save(ProjectAssignment.builder().empresa(empresa).usuario(quien).proyecto(proyecto)
                .fechaInicio(LocalDate.now().minusDays(30)).build());
    }

    private TimeEntry fichar(TimeEntryAction accion, Long proyectoId) {
        return service.registerTimeEntry(ana.getEmail(), new TimeEntryRequest(accion, proyectoId));
    }

    /** Mueve la entrada y el primer tramo {@code horas} hacia atrás. */
    private void retrasar(TimeEntry registro, int horas) {
        jdbc.update("UPDATE registros SET hora_entrada = hora_entrada - make_interval(hours => ?) WHERE id = ?",
                horas, registro.getId());
        jdbc.update("UPDATE tramos_proyecto SET inicio = inicio - make_interval(hours => ?) WHERE registro_id = ?",
                horas, registro.getId());
    }

    @Test
    @DisplayName("Solo se ofrecen los proyectos asignados hoy y activos")
    void proyectosDisponibles() {
        asignar(ana, core);
        asignar(ana, app);
        asignar(ana, cerrado);

        ClockProjectsResponse respuesta = service.proyectosParaFichar(ana.getEmail());

        assertThat(respuesta.disponibles()).extracting(ClockProjectsResponse.ProjectOption::id)
                .containsExactlyInAnyOrder(core.getId(), app.getId());
        assertThat(respuesta.enCurso()).isNull();
    }

    @Test
    @DisplayName("Iniciar en CORE, cambiar a APP con una pausa fichada en APP, cerrar: la suma de imputaciones es el neto")
    void jornadaConCambioDeProyecto() {
        asignar(ana, core);
        asignar(ana, app);

        TimeEntry registro = fichar(TimeEntryAction.INICIO, core.getId());
        assertThat(service.proyectosParaFichar(ana.getEmail()).enCurso().id()).isEqualTo(core.getId());
        retrasar(registro, 5);

        ClockProjectsResponse tras = service.cambiarProyecto(ana.getEmail(), registro.getId(), app.getId());
        assertThat(tras.enCurso().id()).isEqualTo(app.getId());
        // El tramo de APP empieza "ahora": se retrasa 2 h para que dure algo.
        jdbc.update("UPDATE tramos_proyecto SET inicio = inicio - interval '2 hours' WHERE registro_id = ? AND fin IS NULL",
                registro.getId());
        jdbc.update("UPDATE tramos_proyecto SET fin = fin - interval '2 hours' WHERE registro_id = ? AND fin IS NOT NULL",
                registro.getId());

        fichar(TimeEntryAction.PAUSA_INICIO, null);
        jdbc.update("UPDATE registros SET inicio_pausa_actual = inicio_pausa_actual - interval '30 minutes' WHERE id = ?",
                registro.getId());
        fichar(TimeEntryAction.PAUSA_FIN, null);
        TimeEntry cerrada = fichar(TimeEntryAction.FIN, null);

        List<ProjectSegment> tramos = segmentRepository.findByRegistroOrderByInicioAsc(cerrada);
        assertThat(tramos).extracting(t -> t.getProyecto().getId()).containsExactly(core.getId(), app.getId());
        assertThat(tramos.get(1).getSegundosPausa()).isBetween(1795L, 1805L);
        assertThat(tramos).allSatisfy(t -> assertThat(t.getFin()).isNotNull());

        long neto = Duration.between(cerrada.getHoraEntrada(), cerrada.getHoraSalida()).getSeconds()
                - cerrada.getSegundosPausaAcumulados();
        List<ProjectAllocation> imputaciones = allocationRepository.findByRegistroOrderByIdAsc(cerrada);
        assertThat(imputaciones.stream().mapToLong(ProjectAllocation::getSegundos).sum()).isEqualTo(neto);
        // CORE: 3 h; APP: 2 h menos 30 min de pausa.
        assertThat(imputaciones).filteredOn(i -> i.getProyecto().getId() == core.getId())
                .singleElement().satisfies(i -> assertThat(i.getSegundos()).isBetween(3L * 3600 - 5, 3L * 3600 + 5));
        assertThat(imputaciones).filteredOn(i -> i.getProyecto().getId() == app.getId())
                .singleElement().satisfies(i -> assertThat(i.getSegundos()).isBetween(5400L - 5, 5400L + 5));
    }

    @Test
    @DisplayName("Con un solo proyecto se usa sin elegir; con varios y sin elegir, se ficha igual pero sin proyecto")
    void sinElegir() {
        asignar(ana, core);
        TimeEntry uno = fichar(TimeEntryAction.INICIO, null);
        assertThat(service.proyectosParaFichar(ana.getEmail()).enCurso().id()).isEqualTo(core.getId());
        fichar(TimeEntryAction.FIN, null);

        asignar(ana, app);
        TimeEntry varios = fichar(TimeEntryAction.INICIO, null);
        assertThat(varios.getId()).isNotEqualTo(uno.getId());
        assertThat(service.proyectosParaFichar(ana.getEmail()).enCurso()).isNull();
        assertThat(segmentRepository.findByRegistroOrderByInicioAsc(varios)).isEmpty();

        // Elegir después: el proyecto cubre desde la entrada.
        service.cambiarProyecto(ana.getEmail(), varios.getId(), app.getId());
        assertThat(segmentRepository.findByRegistroOrderByInicioAsc(varios)).singleElement()
                .satisfies(t -> assertThat(t.getInicio())
                        // PostgreSQL guarda microsegundos y redondea: 1 ms de margen.
                        .isCloseTo(varios.getHoraEntrada(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS)));
    }

    @Test
    @DisplayName("Reglas: proyecto no asignado 403, mismo proyecto 409, en pausa 409, cerrada 409, de otra persona 403")
    void reglas() {
        asignar(ana, core);
        asignar(ana, app);
        Project ajeno = proyecto("AJENO", true);

        assertThatThrownBy(() -> fichar(TimeEntryAction.INICIO, ajeno.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        TimeEntry registro = fichar(TimeEntryAction.INICIO, core.getId());
        assertThatThrownBy(() -> service.cambiarProyecto(ana.getEmail(), registro.getId(), core.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ya estás");
        assertThatThrownBy(() -> service.cambiarProyecto(ana.getEmail(), registro.getId(), ajeno.getId()))
                .isInstanceOf(BusinessException.class);

        fichar(TimeEntryAction.PAUSA_INICIO, null);
        assertThatThrownBy(() -> service.cambiarProyecto(ana.getEmail(), registro.getId(), app.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Reanuda");
        fichar(TimeEntryAction.PAUSA_FIN, null);

        User javi = persona("javi");
        assertThatThrownBy(() -> service.cambiarProyecto(javi.getEmail(), registro.getId(), app.getId()))
                .isInstanceOf(TenantAccessException.class);

        fichar(TimeEntryAction.FIN, null);
        assertThatThrownBy(() -> service.cambiarProyecto(ana.getEmail(), registro.getId(), app.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cerrada");
    }

    @Test
    @DisplayName("El cambio de proyecto queda en la traza del fichaje")
    void quedaEnLaTraza() {
        asignar(ana, core);
        asignar(ana, app);
        TimeEntry registro = fichar(TimeEntryAction.INICIO, core.getId());

        service.cambiarProyecto(ana.getEmail(), registro.getId(), app.getId());

        String motivo = jdbc.queryForObject(
                "SELECT motivo FROM auditoria_fichaje WHERE registro_id = ? AND accion = 'PROYECTO_CAMBIADO'",
                String.class, registro.getId());
        assertThat(motivo).isEqualTo("De " + core.getCodigo() + " a " + app.getCodigo());
    }
}
