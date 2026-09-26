package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.AnalyticsGrouping;
import com.nxtime.nxtime.domain.AnalyticsPeriod;
import com.nxtime.nxtime.domain.AnalyticsScope;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.HolidayScope;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.ScheduleAssignment;
import com.nxtime.nxtime.domain.ScheduleIncident;
import com.nxtime.nxtime.domain.ScheduleIncidentType;
import com.nxtime.nxtime.domain.ScheduleSlot;
import com.nxtime.nxtime.domain.ScheduleTemplate;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceReasonDays;
import com.nxtime.nxtime.dto.AbsenteeismResponse;
import com.nxtime.nxtime.dto.AbsenteeismRow;
import com.nxtime.nxtime.dto.AnalyticsSummaryResponse;
import com.nxtime.nxtime.dto.PunctualityResponse;
import com.nxtime.nxtime.dto.PunctualityRow;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.AnalyticsRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.repository.ScheduleAssignmentRepository;
import com.nxtime.nxtime.repository.ScheduleIncidentRepository;
import com.nxtime.nxtime.repository.ScheduleSlotRepository;
import com.nxtime.nxtime.repository.ScheduleTemplateRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AnalyticsService;
import com.nxtime.nxtime.service.JornadaTeoricaService;
import com.nxtime.nxtime.service.ScheduleIncidentService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La analítica de punta a punta, contra PostgreSQL real (Fase B4).
 *
 * Una semana de marzo de 2025 con todo lo que mueve las cifras: un festivo el
 * jueves, una persona sin cuadrante con una consulta médica y un día sin
 * fichar, otra con cuadrante que llega tarde dos días y falta el viernes con
 * una explicación que se acepta, y una tercera que entra el miércoles y se va
 * de vacaciones el viernes. Las incidencias las crea el barrido de B2 de
 * verdad, no se insertan a mano: si B2 cambia lo que detecta, esto se entera.
 *
 * <pre>
 *            lun 3   mar 4   mié 5    jue 6    vie 7
 *   Ana      9-17    9-17    —        festivo  MEDICO         (Ventas, sin cuadrante)
 *   Bruno    9-17    9:25    9:45     festivo  — (aceptada)   (Ventas, oficina L-V 9-17)
 *   Carla    ·       ·       9-17     festivo  VACACIONES     (Almacén, empieza el miércoles)
 * </pre>
 *
 * El servicio se construye con un reloj fijo el sábado 8: el periodo MES se
 * cuenta del 1 al 7. Sin reloj fijo, "hasta ayer" sería el 31 de marzo y cada
 * día sin fichar de las tres semanas siguientes contaría.
 */
@SpringBootTest
@DisplayName("Analítica de absentismo y puntualidad")
class AnaliticaIT {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate LUNES = LocalDate.of(2025, 3, 3);
    private static final LocalDate SABADO = LUNES.plusDays(5);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "analitica_it_" + System.nanoTime();
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

    @Autowired private AnalyticsService analyticsService;
    @Autowired private AnalyticsRepository analyticsRepository;
    @Autowired private JornadaTeoricaService jornadaTeoricaService;
    @Autowired private ScheduleIncidentService incidentService;
    @Autowired private ScheduleIncidentRepository incidentRepository;
    @Autowired private ScheduleTemplateRepository templateRepository;
    @Autowired private ScheduleSlotRepository slotRepository;
    @Autowired private ScheduleAssignmentRepository assignmentRepository;
    @Autowired private TimeEntryRepository timeEntryRepository;
    @Autowired private AbsenceRequestRepository absenceRequestRepository;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;

    private Company empresa;
    private Department ventas;
    private User ana;
    private User bruno;
    private User carla;
    private User gestora;
    private User rrhh;
    private AnalyticsServiceImpl elSabado;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ventas = departmentRepository.save(Department.builder().empresa(empresa).nombre("Ventas").build());
        Department almacen = departmentRepository.save(Department.builder().empresa(empresa).nombre("Almacén").build());
        ana = persona("Ana", Role.EMPLEADO, ventas);
        bruno = persona("Bruno", Role.EMPLEADO, ventas);
        carla = persona("Carla", Role.EMPLEADO, almacen);
        gestora = persona("Gestora", Role.GESTOR, ventas);
        rrhh = persona("Rita", Role.RRHH, null);

        holidayRepository.save(Holiday.builder().empresa(empresa).fecha(LUNES.plusDays(3))
                .descripcion("Fiesta local").ambito(HolidayScope.LOCAL).build());

        fichar(ana, LUNES, "09:00", "17:00");
        fichar(ana, LUNES.plusDays(1), "09:00", "17:00");
        ausencia(ana, LUNES.plusDays(4), AbsenceType.MEDICO);

        conCuadranteDeOficina(bruno);
        fichar(bruno, LUNES, "09:00", "17:00");
        fichar(bruno, LUNES.plusDays(1), "09:25", "17:00");
        fichar(bruno, LUNES.plusDays(2), "09:45", "17:00");

        fichar(carla, LUNES.plusDays(2), "09:00", "17:00");
        ausencia(carla, LUNES.plusDays(4), AbsenceType.VACACIONES);

        // El barrido de B2, de verdad, y la gestora acepta la explicación de
        // la falta del viernes de Bruno.
        incidentService.detectar(LUNES, LUNES.plusDays(4));
        ScheduleIncident falta = incidentRepository.findDeUsuarioEnRango(bruno.getId(), LUNES, SABADO).stream()
                .filter(i -> i.getTipo() == ScheduleIncidentType.AUSENCIA)
                .findFirst().orElseThrow();
        incidentService.resolver(falta.getId(), true, null, gestora);

        elSabado = new AnalyticsServiceImpl(analyticsRepository, userRepository, jornadaTeoricaService,
                Clock.fixed(SABADO.atTime(10, 0).atZone(MADRID).toInstant(), MADRID));
    }

    @Test
    @DisplayName("RRHH ve la empresa: 9 días laborables, 3 perdidos, y el desglose de por qué")
    void absentismo_empresa() {
        AbsenteeismResponse respuesta =
                elSabado.absentismo(rrhh, AnalyticsPeriod.MES, LUNES, AnalyticsGrouping.DEPARTAMENTO);

        assertThat(respuesta.ventana().alcance()).isEqualTo(AnalyticsScope.EMPRESA);
        assertThat(respuesta.ventana().desde()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(respuesta.ventana().evaluadoHasta()).isEqualTo(LocalDate.of(2025, 3, 7));

        AbsenteeismRow total = respuesta.total();
        // Ni la gestora ni RRHH han fichado nunca: no entran en la cuenta.
        assertThat(total.personas()).isEqualTo(3);
        assertThat(total.diasLaborables()).isEqualTo(9);
        assertThat(total.diasTrabajados()).isEqualTo(6);
        assertThat(total.diasAusenciaJustificada()).isEqualTo(2);
        assertThat(total.diasSinFichaje()).isEqualTo(1);
        assertThat(total.diasVacaciones()).isEqualTo(1);
        assertThat(total.absentismo()).isEqualByComparingTo("33.3");
        assertThat(total.absentismoSinJustificar()).isEqualByComparingTo("11.1");
        assertThat(total.motivos())
                .extracting(AbsenceReasonDays::motivo, AbsenceReasonDays::dias)
                .containsExactlyInAnyOrder(tuple("MEDICO", 1), tuple("INCIDENCIA_ACEPTADA", 1));

        // Carla empezó el miércoles: el lunes y el martes no se le deben.
        assertThat(respuesta.filas())
                .extracting(AbsenteeismRow::nombre, AbsenteeismRow::diasLaborables, AbsenteeismRow::absentismo)
                .containsExactly(
                        tuple("Almacén", 1, new BigDecimal("0.0")),
                        tuple("Ventas", 8, new BigDecimal("37.5")));
    }

    @Test
    @DisplayName("Por persona: a Ana le falta un día sin nada y a Bruno uno con la explicación aceptada")
    void absentismo_porPersona() {
        AbsenteeismResponse respuesta =
                elSabado.absentismo(rrhh, AnalyticsPeriod.MES, LUNES, AnalyticsGrouping.EMPLEADO);

        assertThat(respuesta.filas())
                .extracting(AbsenteeismRow::id, AbsenteeismRow::diasLaborables, AbsenteeismRow::diasSinFichaje,
                        AbsenteeismRow::diasAusenciaJustificada)
                .containsExactly(
                        tuple(ana.getId(), 4, 1, 1),
                        tuple(bruno.getId(), 4, 0, 1),
                        tuple(carla.getId(), 1, 0, 0));
    }

    @Test
    @DisplayName("Una gestora ve solo su departamento, y lo dice")
    void absentismo_gestora() {
        AbsenteeismResponse respuesta =
                elSabado.absentismo(gestora, AnalyticsPeriod.MES, LUNES, AnalyticsGrouping.EMPLEADO);

        assertThat(respuesta.ventana().alcance()).isEqualTo(AnalyticsScope.DEPARTAMENTO);
        assertThat(respuesta.ventana().departamento()).isEqualTo("Ventas");
        assertThat(respuesta.filas()).extracting(AbsenteeismRow::id).containsExactly(ana.getId(), bruno.getId());
        assertThat(respuesta.total().absentismo()).isEqualByComparingTo("37.5");
    }

    @Test
    @DisplayName("Una gestora sin departamento no ve la empresa ni un cero: recibe un 409")
    void gestoraSinDepartamento() {
        User sinDepartamento = persona("Sin", Role.GESTOR, null);

        assertThatThrownBy(() -> elSabado.resumen(sinDepartamento, AnalyticsPeriod.MES, LUNES))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("departamento");
    }

    @Test
    @DisplayName("Puntualidad: de tres entradas con horario, dos tarde (25 y 45 minutos)")
    void puntualidad() {
        PunctualityResponse respuesta =
                elSabado.puntualidad(rrhh, AnalyticsPeriod.MES, LUNES, AnalyticsGrouping.EMPLEADO);

        PunctualityRow total = respuesta.total();
        assertThat(total.entradasConHorario()).isEqualTo(3);
        assertThat(total.retrasos()).isEqualTo(2);
        assertThat(total.puntuales()).isEqualTo(1);
        assertThat(total.puntualidad()).isEqualByComparingTo("33.3");
        assertThat(total.retrasoMedioMinutos()).isEqualTo(35.0);
        assertThat(total.retrasoMedianoMinutos()).isEqualTo(35.0);
        assertThat(total.retrasosHasta30()).isEqualTo(1);
        assertThat(total.retrasosDeMasDe30()).isEqualTo(1);

        // Sin cuadrante no hay hora a la que llegar tarde: Ana no tiene entradas con horario.
        assertThat(respuesta.filas())
                .extracting(PunctualityRow::id, PunctualityRow::entradasConHorario, PunctualityRow::puntualidad)
                .containsExactly(
                        tuple(ana.getId(), 0, null),
                        tuple(bruno.getId(), 3, new BigDecimal("33.3")),
                        tuple(carla.getId(), 0, null));
    }

    @Test
    @DisplayName("El resumen cuadra con el total de las otras dos y añade el contexto de las jornadas")
    void resumen() {
        AnalyticsSummaryResponse resumen = elSabado.resumen(rrhh, AnalyticsPeriod.MES, LUNES);

        assertThat(resumen.personas()).isEqualTo(3);
        assertThat(resumen.absentismo()).isEqualByComparingTo("33.3");
        assertThat(resumen.puntualidad()).isEqualByComparingTo("33.3");
        assertThat(resumen.retrasoMedioMinutos()).isEqualTo(35.0);
        assertThat(resumen.jornadasIncompletas()).isEqualByComparingTo("0.0");
        // 480 + 480 (Ana) + 480 + 455 + 435 (Bruno) + 480 (Carla) = 2810 minutos en 6 días.
        assertThat(resumen.minutosMediosPorDia()).isEqualTo(468L);
    }

    @Test
    @DisplayName("El día 1 del mes no hay ningún día terminado: todo null, no ceros")
    void primerDiaDelMes() {
        AnalyticsServiceImpl elDiaUno = new AnalyticsServiceImpl(analyticsRepository, userRepository,
                jornadaTeoricaService, Clock.fixed(Instant.parse("2025-03-01T10:00:00Z"), MADRID));

        AnalyticsSummaryResponse resumen = elDiaUno.resumen(rrhh, AnalyticsPeriod.MES, LocalDate.of(2025, 3, 1));

        assertThat(resumen.ventana().evaluadoHasta()).isNull();
        assertThat(resumen.personas()).isZero();
        assertThat(resumen.absentismo()).isNull();
        assertThat(resumen.puntualidad()).isNull();
    }

    @Test
    @DisplayName("Por el bean de Spring la respuesta sale de la caché la segunda vez, y resolver una incidencia la vacía")
    void cache() {
        AnalyticsSummaryResponse primera = analyticsService.resumen(rrhh, AnalyticsPeriod.MES, LUNES);
        assertThat(analyticsService.resumen(rrhh, AnalyticsPeriod.MES, LUNES)).isSameAs(primera);

        ScheduleIncident retraso = incidentRepository.findDeUsuarioEnRango(bruno.getId(), LUNES, SABADO).stream()
                .filter(i -> i.getTipo() == ScheduleIncidentType.RETRASO)
                .findFirst().orElseThrow();
        incidentService.resolver(retraso.getId(), true, null, gestora);

        assertThat(analyticsService.resumen(rrhh, AnalyticsPeriod.MES, LUNES)).isNotSameAs(primera);
    }

    // ------------------------------------------------------------------

    private User persona(String nombre, Role rol, Department departamento) {
        return userRepository.save(User.builder()
                .nombre(nombre).email(nombre.toLowerCase() + System.nanoTime() + "@test").contrasena("x")
                .rol(rol).empresa(empresa).departamento(departamento).activo(true)
                .horasSemanales(new BigDecimal("40.0")).build());
    }

    private void conCuadranteDeOficina(User persona) {
        ScheduleTemplate oficina = templateRepository.save(ScheduleTemplate.builder()
                .empresa(empresa).nombre("Oficina " + System.nanoTime()).build());
        for (short dia = 1; dia <= 5; dia++) {
            slotRepository.save(ScheduleSlot.builder().plantilla(oficina).diaSemana(dia).inicio(540).fin(1020).build());
        }
        assignmentRepository.save(ScheduleAssignment.builder()
                .empresa(empresa).usuario(persona).plantilla(oficina).fechaInicio(LUNES).build());
    }

    private void fichar(User persona, LocalDate dia, String entrada, String salida) {
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(persona).empresa(empresa)
                .horaEntrada(ZonedDateTime.of(dia, LocalTime.parse(entrada), MADRID).toInstant())
                .horaSalida(ZonedDateTime.of(dia, LocalTime.parse(salida), MADRID).toInstant())
                .segundosPausaAcumulados(0).enPausa(false).build());
    }

    private void ausencia(User persona, LocalDate dia, AbsenceType tipo) {
        absenceRequestRepository.save(AbsenceRequest.builder()
                .usuario(persona).empresa(empresa).fechaInicio(dia).fechaFin(dia).tipo(tipo)
                .estado(AbsenceStatus.APROBADA).aprobadoPor(rrhh).fechaResolucion(Instant.now()).build());
    }
}
