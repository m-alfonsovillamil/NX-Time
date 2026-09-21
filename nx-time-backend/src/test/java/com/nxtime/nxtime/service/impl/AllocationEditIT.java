package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.CorrectionStatus;
import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.ProjectAllocation;
import com.nxtime.nxtime.domain.ProjectAssignment;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AllocationsResponse;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.ResolveCorrectionRequest;
import com.nxtime.nxtime.dto.SetAllocationsRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.ProjectAllocationRepository;
import com.nxtime.nxtime.repository.ProjectAssignmentRepository;
import com.nxtime.nxtime.repository.ProjectRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AllocationEditService;
import com.nxtime.nxtime.service.CorrectionService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
 * Repartir a mano las horas de una jornada entre proyectos (ADR 017), contra
 * PostgreSQL real.
 *
 * Los tres caminos: se aplica solo, se pide porque la jornada es de otra
 * semana, y se pide porque suman más horas de las fichadas.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class AllocationEditIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "reparto_it_" + System.nanoTime();
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

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired
    private AllocationEditService service;
    @Autowired
    private CorrectionService correctionService;
    @Autowired
    private ProjectAllocationRepository allocationRepository;
    @Autowired
    private ProjectAssignmentRepository assignmentRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Company empresa;
    private User ana;
    private User gestora;
    private Project core;
    private Project app;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ana = persona("ana", Role.EMPLEADO);
        gestora = persona("marta", Role.GESTOR);
        core = proyecto("CORE");
        app = proyecto("APP");
        asignar(core);
        asignar(app);
    }

    private User persona(String nombre, Role rol) {
        return userRepository.save(User.builder()
                .nombre(nombre).email(nombre + System.nanoTime() + "@test").contrasena("x")
                .rol(rol).empresa(empresa).activo(true).horasSemanales(new BigDecimal("40.0")).build());
    }

    private Project proyecto(String codigo) {
        return projectRepository.save(Project.builder()
                .empresa(empresa).codigo(codigo + System.nanoTime() % 100000).nombre(codigo)
                .fechaInicio(LocalDate.of(2020, 1, 1)).activo(true).build());
    }

    private void asignar(Project proyecto) {
        assignmentRepository.save(ProjectAssignment.builder().empresa(empresa).usuario(ana).proyecto(proyecto)
                .fechaInicio(LocalDate.of(2020, 1, 1)).build());
    }

    /** Una jornada de 8 h que empieza a las 8:00 de Madrid del día indicado. */
    private TimeEntry jornada(LocalDate dia) {
        Instant entrada = dia.atTime(8, 0).atZone(MADRID).toInstant();
        return timeEntryRepository.save(TimeEntry.builder()
                .usuario(ana).empresa(empresa)
                .horaEntrada(entrada).horaSalida(entrada.plusSeconds(8 * 3600))
                .build());
    }

    private LocalDate lunesDeEstaSemana() {
        return LocalDate.now(MADRID).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private SetAllocationsRequest reparto(long minutosCore, long minutosApp, String motivo) {
        return new SetAllocationsRequest(List.of(
                new SetAllocationsRequest.Linea(core.getId(), minutosCore),
                new SetAllocationsRequest.Linea(app.getId(), minutosApp)), motivo);
    }

    private Map<String, Long> imputado(TimeEntry registro) {
        return allocationRepository.findByRegistroOrderByIdAsc(registro).stream()
                .collect(Collectors.toMap(i -> i.getProyecto().getNombre(), ProjectAllocation::getSegundos));
    }

    @Test
    @DisplayName("Una jornada de esta semana se reparte al momento, y queda en la traza")
    void repartoLibre() {
        TimeEntry registro = jornada(lunesDeEstaSemana());

        AllocationEditService.Resultado resultado = service.repartir(registro.getId(), reparto(240, 240, null), ana);

        assertThat(resultado.solicitud()).isNull();
        assertThat(resultado.aplicado().netoMinutos()).isEqualTo(480);
        assertThat(imputado(registro)).containsOnly(
                Map.entry("CORE", 4L * 3600), Map.entry("APP", 4L * 3600));
        assertThat(allocationRepository.findByRegistroOrderByIdAsc(registro))
                .allSatisfy(i -> assertThat(i.getOrigen()).isEqualTo(ProjectAllocation.Origen.MANUAL));

        String motivo = jdbc.queryForObject(
                "SELECT motivo FROM auditoria_fichaje WHERE registro_id = ? AND accion = 'REPARTO_PROYECTOS'",
                String.class, registro.getId());
        assertThat(motivo).contains("Reparto por proyecto:").contains("240 min");
    }

    @Test
    @DisplayName("De una semana anterior no se aplica: se pide, y al aprobarla queda repartida")
    void fueraDePlazo_pasaPorAprobacion() {
        TimeEntry registro = jornada(lunesDeEstaSemana().minusDays(10));

        assertThatThrownBy(() -> service.repartir(registro.getId(), reparto(240, 240, null), ana))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        AllocationEditService.Resultado resultado =
                service.repartir(registro.getId(), reparto(180, 300, "Estuve en los dos proyectos"), ana);

        assertThat(resultado.aplicado()).isNull();
        assertThat(resultado.solicitud().estado()).isEqualTo(CorrectionStatus.PENDIENTE);
        assertThat(resultado.solicitud().repartoPropuesto()).hasSize(2);
        // Las horas propuestas son las mismas: solo cambia el reparto.
        assertThat(resultado.solicitud().horaSalidaPropuesta()).isEqualTo(registro.getHoraSalida());
        // Y todavía no se ha tocado nada.
        assertThat(imputado(registro)).isEmpty();

        correctionService.resolver(resultado.solicitud().id(), new ResolveCorrectionRequest(true, null), gestora);

        TimeEntry corregida = timeEntryRepository.findByRegistroOriginal_Id(registro.getId()).orElseThrow();
        assertThat(imputado(corregida)).containsOnly(
                Map.entry("CORE", 3L * 3600), Map.entry("APP", 5L * 3600));
        assertThat(imputado(registro)).isEmpty();
    }

    @Test
    @DisplayName("Si el reparto suma más horas de las fichadas, se pide como corrección y amplía la salida")
    void masHorasDeLasFichadas() {
        TimeEntry registro = jornada(lunesDeEstaSemana());

        AllocationEditService.Resultado resultado =
                service.repartir(registro.getId(), reparto(240, 300, "Seguí trabajando sin fichar"), ana);

        assertThat(resultado.aplicado()).isNull();
        // 9 h: la salida se amplía lo justo para que quepan.
        assertThat(resultado.solicitud().horaSalidaPropuesta())
                .isEqualTo(registro.getHoraEntrada().plusSeconds(9 * 3600));

        correctionService.resolver(resultado.solicitud().id(), new ResolveCorrectionRequest(true, null), gestora);

        TimeEntry corregida = timeEntryRepository.findByRegistroOriginal_Id(registro.getId()).orElseThrow();
        assertThat(imputado(corregida)).containsOnly(
                Map.entry("CORE", 4L * 3600), Map.entry("APP", 5L * 3600));
    }

    @Test
    @DisplayName("Reglas: menos horas 400, proyecto ajeno 403, jornada abierta 409, de otra persona 403")
    void reglas() {
        TimeEntry registro = jornada(lunesDeEstaSemana());

        assertThatThrownBy(() -> service.repartir(registro.getId(), reparto(120, 120, "Menos"), ana))
                .isInstanceOf(BusinessException.class).hasMessageContaining("menos de lo trabajado");

        Project ajeno = proyecto("AJENO");
        assertThatThrownBy(() -> service.repartir(registro.getId(),
                new SetAllocationsRequest(List.of(new SetAllocationsRequest.Linea(ajeno.getId(), 480)), null), ana))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.repartir(registro.getId(),
                new SetAllocationsRequest(List.of(new SetAllocationsRequest.Linea(core.getId(), 0)), null), ana))
                .isInstanceOf(BusinessException.class).hasMessageContaining("todo a cero");

        TimeEntry abierta = timeEntryRepository.save(TimeEntry.builder()
                .usuario(ana).empresa(empresa).horaEntrada(Instant.now()).build());
        assertThatThrownBy(() -> service.repartir(abierta.getId(), reparto(240, 240, null), ana))
                .isInstanceOf(BusinessException.class).hasMessageContaining("abierta");

        assertThatThrownBy(() -> service.repartir(registro.getId(), reparto(240, 240, null), gestora))
                .isInstanceOf(TenantAccessException.class);
    }

    /**
     * El agujero que tenía el otro camino (Fase A3).
     *
     * La regla «solo los proyectos que tenías asignados ese día» se comprobaba
     * al repartir, pero no al pedir una corrección con el reparto dentro — y
     * {@code POST /api/v1/fichaje/{id}/correcciones} acepta ese reparto del
     * cliente. Bastaba un cliente HTTP para imputar horas a cualquier proyecto
     * de la empresa, y al aprobarse la corrección se escribía sin volver a
     * mirar.
     *
     * Este caso va junto al de arriba a propósito: son la misma regla, y
     * separarlos es como una de las dos se quedó atrás.
     */
    @Test
    @DisplayName("La corrección con reparto exige lo mismo: un proyecto no asignado ese día es 403")
    void correccionConRepartoAProyectoNoAsignado_da403() {
        TimeEntry registro = jornada(lunesDeEstaSemana());
        Project enElQueNuncaEstuvo = proyecto("NUNCA");

        // De la misma empresa: lo único que el código comprobaba antes.
        assertThat(enElQueNuncaEstuvo.getEmpresa().getId()).isEqualTo(empresa.getId());

        // Por delta y no en absoluto: este IT no hace rollback entre casos, así
        // que la tabla ya trae filas de los anteriores.
        long repartosAntes = repartosPropuestos();

        assertThatThrownBy(() -> correctionService.solicitar(registro.getId(),
                new CorrectionRequestDTO(registro.getHoraEntrada(), registro.getHoraSalida(),
                        "Me equivoqué de proyecto", null, null,
                        List.of(new CorrectionRequestDTO.ProjectShare(enElQueNuncaEstuvo.getId(), 480))),
                ana))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no estabas asignado")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        // Y no queda ni rastro: la transacción del servicio se deshace entera,
        // así que tampoco se guarda el reparto rechazado.
        assertThat(repartosPropuestos())
                .as("un reparto rechazado no puede dejar filas")
                .isEqualTo(repartosAntes);
    }

    private long repartosPropuestos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM repartos_propuestos", Long.class);
    }

    @Test
    @DisplayName("La corrección con reparto a un proyecto sí asignado se acepta")
    void correccionConRepartoValido_seAcepta() {
        TimeEntry registro = jornada(lunesDeEstaSemana());

        var solicitud = correctionService.solicitar(registro.getId(),
                new CorrectionRequestDTO(registro.getHoraEntrada(), registro.getHoraSalida(),
                        "Repartir entre los dos", null, null,
                        List.of(new CorrectionRequestDTO.ProjectShare(core.getId(), 240),
                                new CorrectionRequestDTO.ProjectShare(app.getId(), 240))),
                ana);

        assertThat(solicitud.repartoPropuesto()).hasSize(2);
    }

    @Test
    @DisplayName("Con una corrección viva no se reparte, y la consulta lo dice")
    void conCorreccionViva() {
        TimeEntry registro = jornada(lunesDeEstaSemana().minusDays(10));
        var solicitud = service.repartir(registro.getId(), reparto(240, 240, "Reparto"), ana).solicitud();

        AllocationsResponse estado = service.deLaJornada(registro.getId(), ana);
        assertThat(estado.solicitudPendienteId()).isEqualTo(solicitud.id());
        assertThat(estado.repartoLibre()).isFalse();
        assertThat(estado.disponibles()).hasSize(2);

        assertThatThrownBy(() -> service.repartir(registro.getId(), reparto(300, 180, "Otro"), ana))
                .isInstanceOf(BusinessException.class).hasMessageContaining("sin resolver");
    }
}
