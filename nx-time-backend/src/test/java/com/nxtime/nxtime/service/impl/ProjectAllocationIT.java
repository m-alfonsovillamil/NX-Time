package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.AddedPause;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.ProjectAllocation;
import com.nxtime.nxtime.domain.ProjectAllocation.Origen;
import com.nxtime.nxtime.domain.ProjectAssignment;
import com.nxtime.nxtime.domain.ProjectSegment;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.AddedPauseRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.ProjectAllocationRepository;
import com.nxtime.nxtime.repository.ProjectAssignmentRepository;
import com.nxtime.nxtime.repository.ProjectRepository;
import com.nxtime.nxtime.repository.ProjectSegmentRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.ProjectAllocationService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Las imputaciones de horas a proyectos (ADR 017), contra PostgreSQL real.
 *
 * Lo que tiene que cumplirse siempre: en una jornada cerrada con imputaciones,
 * la suma es el neto. Y que las horas no se pierdan de los informes al
 * corregir la jornada.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class ProjectAllocationIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "imputaciones_it_" + System.nanoTime();
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
    private ProjectAllocationService service;
    @Autowired
    private TransactionTemplate transaccion;
    @Autowired
    private ProjectAllocationRepository allocationRepository;
    @Autowired
    private ProjectSegmentRepository segmentRepository;
    @Autowired
    private ProjectAssignmentRepository assignmentRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private AddedPauseRepository addedPauseRepository;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    private Company empresa;
    private User ana;
    private Project core;
    private Project app;

    private static final Instant OCHO = Instant.parse("2026-06-10T06:00:00Z");    // 08:00 en Madrid
    private static final Instant DOCE = Instant.parse("2026-06-10T10:00:00Z");
    private static final Instant DIECISEIS = Instant.parse("2026-06-10T14:00:00Z");

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ana = userRepository.save(User.builder()
                .nombre("Ana").email("ana" + System.nanoTime() + "@test").contrasena("x")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).horasSemanales(new BigDecimal("40.0")).build());
        core = proyecto("CORE");
        app = proyecto("APP");
    }

    private Project proyecto(String codigo) {
        return projectRepository.save(Project.builder()
                .empresa(empresa).codigo(codigo + System.nanoTime()).nombre(codigo)
                .fechaInicio(LocalDate.of(2026, 1, 1)).activo(true).build());
    }

    private void asignar(Project proyecto) {
        assignmentRepository.save(ProjectAssignment.builder()
                .empresa(empresa).usuario(ana).proyecto(proyecto).fechaInicio(LocalDate.of(2026, 1, 1)).build());
    }

    private TimeEntry jornada(long segundosPausa) {
        return timeEntryRepository.save(TimeEntry.builder()
                .usuario(ana).empresa(empresa).horaEntrada(OCHO).horaSalida(DIECISEIS)
                .segundosPausaAcumulados(segundosPausa).build());
    }

    private Map<String, Long> reparto(TimeEntry registro) {
        return allocationRepository.findByRegistroOrderByIdAsc(registro).stream()
                .collect(Collectors.toMap(i -> i.getProyecto().getNombre(), ProjectAllocation::getSegundos));
    }

    private void enTransaccion(Runnable accion) {
        transaccion.executeWithoutResult(estado -> accion.run());
    }

    @Test
    @DisplayName("Con un solo proyecto asignado y sin tramos, todo el neto va a ese proyecto")
    void unSoloProyecto_todoAEse() {
        asignar(core);
        TimeEntry registro = jornada(1800);

        enTransaccion(() -> service.alCerrar(registro));

        assertThat(reparto(registro)).containsExactly(Map.entry("CORE", 8L * 3600 - 1800));
    }

    /* Inventarse un reparto sería peor que dejar las horas sin proyecto. */
    @Test
    @DisplayName("Con varios proyectos y sin tramos no se imputa nada")
    void variosSinTramos_nada() {
        asignar(core);
        asignar(app);
        TimeEntry registro = jornada(0);

        enTransaccion(() -> service.alCerrar(registro));

        assertThat(reparto(registro)).isEmpty();
    }

    @Test
    @DisplayName("Con tramos: cada tramo menos sus pausas fichadas y lo que le solapen las añadidas; suma = neto")
    void desdeTramos() {
        TimeEntry registro = jornada(1800 + 1800);
        // CORE de 8 a 12 con 30 min de pausa fichada; APP de 12 a 16 (abierto al cerrar).
        segmentRepository.save(ProjectSegment.builder().empresa(empresa).registro(registro).proyecto(core)
                .inicio(OCHO).fin(DOCE).segundosPausa(1800).build());
        segmentRepository.save(ProjectSegment.builder().empresa(empresa).registro(registro).proyecto(app)
                .inicio(DOCE).build());
        // Pausa añadida a mano de 9:00 a 9:30 en Madrid: cae en CORE, que es el
        // tramo MENOR. Si cayera en el mayor, el ajuste final a la suma taparía
        // un cálculo que ignorase el solape, y el test no lo vería.
        addedPauseRepository.save(AddedPause.builder().empresa(empresa).registro(registro)
                .inicio(OCHO.plusSeconds(3600)).fin(OCHO.plusSeconds(5400)).motivo("Médico").creadaPor(ana).build());

        enTransaccion(() -> service.alCerrar(registro));

        assertThat(reparto(registro)).containsOnly(
                Map.entry("CORE", 4L * 3600 - 1800 - 1800),
                Map.entry("APP", 4L * 3600));
        assertThat(segmentRepository.findByRegistroAndFinIsNull(registro)).isEmpty();
    }

    @Test
    @DisplayName("Un reparto MANUAL se reescala en proporción si cambian las pausas, y sigue sumando el neto")
    void manual_seReescala() {
        TimeEntry registro = jornada(0);
        allocationRepository.saveAll(List.of(
                ProjectAllocation.builder().empresa(empresa).registro(registro).proyecto(core)
                        .segundos(6L * 3600).origen(Origen.MANUAL).build(),
                ProjectAllocation.builder().empresa(empresa).registro(registro).proyecto(app)
                        .segundos(2L * 3600).origen(Origen.MANUAL).build()));
        // Se añaden 2 h de pausa: el neto baja de 8 h a 6 h.
        registro.setSegundosPausaAcumulados(2L * 3600);
        timeEntryRepository.save(registro);

        enTransaccion(() -> service.alCambiarPausas(registro));

        assertThat(reparto(registro)).containsOnly(
                Map.entry("CORE", (long) (4.5 * 3600)),
                Map.entry("APP", (long) (1.5 * 3600)));
    }

    /*
     * El caso que más duele si se rompe: corregir una jornada la anula y crea
     * otra. Si las imputaciones no se mudaran, sus horas desaparecerían del
     * informe por proyecto.
     */
    @Test
    @DisplayName("Al corregir, tramos e imputaciones se mudan a la jornada nueva y se reescalan a su neto")
    void alCorregir_seMudanYSeReescalan() {
        asignar(core);
        TimeEntry original = jornada(0);
        enTransaccion(() -> service.alCerrar(original));
        segmentRepository.save(ProjectSegment.builder().empresa(empresa).registro(original).proyecto(core)
                .inicio(OCHO).fin(DIECISEIS).build());

        original.setAnulado(true);
        timeEntryRepository.save(original);
        // La corregida dura 6 h.
        TimeEntry corregida = timeEntryRepository.save(TimeEntry.builder()
                .usuario(ana).empresa(empresa).horaEntrada(OCHO).horaSalida(DOCE.plusSeconds(7200))
                .registroOriginal(original).build());

        enTransaccion(() -> service.alCorregir(original, corregida));

        assertThat(reparto(original)).isEmpty();
        assertThat(reparto(corregida)).containsExactly(Map.entry("CORE", 6L * 3600));
        assertThat(allocationRepository.findByRegistroOrderByIdAsc(corregida))
                .allSatisfy(i -> assertThat(i.getOrigen()).isEqualTo(Origen.MANUAL));
        assertThat(segmentRepository.findByRegistroOrderByInicioAsc(corregida)).hasSize(1);

        // Y el informe de la empresa cuenta la versión corregida, una sola vez.
        var horas = allocationRepository.sumarSegundosPorProyecto(
                empresa.getId(), OCHO.minusSeconds(86400), OCHO.plusSeconds(86400));
        assertThat(horas).singleElement().satisfies(fila -> assertThat(fila.getSegundos()).isEqualTo(6L * 3600));
    }

    /* Con la jornada abierta no hay neto: nada que imputar todavía. */
    @Test
    @DisplayName("Cambiar pausas con la jornada abierta no imputa nada")
    void jornadaAbierta_nada() {
        asignar(core);
        TimeEntry abierta = timeEntryRepository.save(TimeEntry.builder()
                .usuario(ana).empresa(empresa).horaEntrada(OCHO).build());

        enTransaccion(() -> service.alCambiarPausas(abierta));

        assertThat(reparto(abierta)).isEmpty();
    }
}
