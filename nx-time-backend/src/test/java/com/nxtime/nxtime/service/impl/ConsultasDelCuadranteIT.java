package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.ScheduleAssignment;
import com.nxtime.nxtime.domain.ScheduleException;
import com.nxtime.nxtime.domain.ScheduleExceptionType;
import com.nxtime.nxtime.domain.ScheduleSlot;
import com.nxtime.nxtime.domain.ScheduleTemplate;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TeamScheduleEntryResponse;
import com.nxtime.nxtime.dto.TheoreticalDayResponse;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.ScheduleAssignmentRepository;
import com.nxtime.nxtime.repository.ScheduleExceptionRepository;
import com.nxtime.nxtime.repository.ScheduleSlotRepository;
import com.nxtime.nxtime.repository.ScheduleTemplateRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.JornadaTeoricaService;
import com.nxtime.nxtime.service.ScheduleService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cuántas consultas cuesta el cuadrante del equipo de un día (revisión del
 * 28/11/2026).
 *
 * <p>{@code ScheduleService.equipo} lo usan la semana de cuadrantes de la web
 * (siete llamadas, una por día) y "Ahora mismo", que se refresca cada minuto.
 * Calculaba el día de cada persona por separado: con 87 personas, 216
 * consultas por llamada. Este test fija que el número de consultas no crezca
 * con la plantilla, y que el cálculo por lotes dé lo mismo que el de una
 * persona: festivo, ausencia, medio día, excepción y plantilla.
 *
 * <p>Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ConsultasDelCuadranteIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "cuadrante_consultas_it_" + System.nanoTime();
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

    /** Un miércoles cualquiera de dentro de unas semanas: laborable y sin festivos nacionales cerca. */
    private static final LocalDate DIA = LocalDate.of(2027, 3, 1).with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));

    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ScheduleTemplateRepository templateRepository;
    @Autowired private ScheduleSlotRepository slotRepository;
    @Autowired private ScheduleAssignmentRepository assignmentRepository;
    @Autowired private ScheduleExceptionRepository exceptionRepository;
    @Autowired private AbsenceRequestRepository absenceRequestRepository;
    @Autowired private ScheduleService scheduleService;
    @Autowired private JornadaTeoricaService jornadaTeoricaService;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Company empresa;
    private User rrhh;
    private ScheduleTemplate oficina;
    private final List<User> plantilla = new ArrayList<>();

    @BeforeEach
    void preparar() {
        empresa = companyRepository.save(Company.builder().nombre("Cuadrante " + System.nanoTime()).build());
        rrhh = persona("Elena", Role.RRHH);
        oficina = templateRepository.save(ScheduleTemplate.builder()
                .empresa(empresa).nombre("Oficina " + System.nanoTime()).build());
        for (short dia = 1; dia <= 5; dia++) {
            slotRepository.save(ScheduleSlot.builder().plantilla(oficina).diaSemana(dia).inicio(540).fin(1020).build());
        }
    }

    @Test
    @DisplayName("El cuadrante del equipo cuesta las mismas consultas con 4 personas que con 16")
    void lasConsultasNoCrecenConLaPlantilla() {
        conCuadrante(4);
        long conCuatro = consultasDe(() -> scheduleService.equipo(DIA, rrhh));

        conCuadrante(12);
        long conDieciseis = consultasDe(() -> scheduleService.equipo(DIA, rrhh));

        assertThat(scheduleService.equipo(DIA, rrhh)).hasSize(16);
        // Menor o igual, no igual: la primera llamada puede llevar alguna
        // consulta de calentamiento (8 con 4 personas y 7 con 16 al medirlo).
        // Lo que no puede es crecer: antes eran unas dos y media por persona.
        assertThat(conDieciseis)
                .as("consultas con 16 personas frente a %d con 4", conCuatro)
                .isLessThanOrEqualTo(conCuatro);
        assertThat(conCuatro).as("consultas por llamada").isLessThanOrEqualTo(12);
    }

    @Test
    @DisplayName("Por lotes sale lo mismo que persona a persona: ausencia, medio día, excepción y plantilla")
    void porLotesSaleLoMismoQuePersonaAPersona() {
        conCuadrante(4);
        User deVacaciones = plantilla.get(0);
        User conMedioDia = plantilla.get(1);
        User conExcepcion = plantilla.get(2);
        ausencia(deVacaciones, false);
        ausencia(conMedioDia, true);
        exceptionRepository.save(ScheduleException.builder().empresa(empresa).usuario(conExcepcion).fecha(DIA)
                .tipo(ScheduleExceptionType.LIBRE).motivo("Cambio de turno").creadaPor(rrhh).build());

        Map<Long, TheoreticalDayResponse> porLotes = scheduleService.equipo(DIA, rrhh).stream()
                .collect(Collectors.toMap(TeamScheduleEntryResponse::usuarioId, TeamScheduleEntryResponse::dia));

        assertThat(porLotes).hasSize(4);
        for (User persona : plantilla) {
            TheoreticalDayResponse unoAUno = ScheduleServiceImpl.toResponse(jornadaTeoricaService.dia(persona, DIA));
            assertThat(porLotes.get(persona.getId())).as(persona.getNombre()).isEqualTo(unoAUno);
        }
        // Y que los casos de verdad son distintos entre sí (si no, comparar no prueba nada).
        assertThat(porLotes.values().stream().map(TheoreticalDayResponse::origen).distinct().count())
                .isGreaterThanOrEqualTo(3);
    }

    // ------------------------------------------------------------------

    private void conCuadrante(int cuantas) {
        for (int i = 0; i < cuantas; i++) {
            User persona = persona("Persona" + plantilla.size(), Role.EMPLEADO);
            assignmentRepository.save(ScheduleAssignment.builder()
                    .empresa(empresa).usuario(persona).plantilla(oficina).fechaInicio(DIA.minusMonths(1)).build());
            plantilla.add(persona);
        }
    }

    private void ausencia(User persona, boolean medioDia) {
        absenceRequestRepository.save(AbsenceRequest.builder()
                .usuario(persona).empresa(empresa).fechaInicio(DIA).fechaFin(DIA).tipo(AbsenceType.VACACIONES)
                .medioDia(medioDia).estado(AbsenceStatus.APROBADA).aprobadoPor(rrhh)
                .fechaResolucion(Instant.now()).build());
    }

    private User persona(String nombre, Role rol) {
        return userRepository.save(User.builder()
                .nombre(nombre).email(nombre.toLowerCase() + "." + System.nanoTime() + "@nxtime.test").contrasena("x")
                .rol(rol).empresa(empresa).activo(true).horasSemanales(new BigDecimal("40.0")).build());
    }

    private long consultasDe(Runnable llamada) {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();
        llamada.run();
        return estadisticas.getPrepareStatementCount();
    }
}
