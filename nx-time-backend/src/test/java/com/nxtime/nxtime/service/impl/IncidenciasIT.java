package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.HolidayScope;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.ScheduleAssignment;
import com.nxtime.nxtime.domain.ScheduleIncident;
import com.nxtime.nxtime.domain.ScheduleIncidentStatus;
import com.nxtime.nxtime.domain.ScheduleIncidentType;
import com.nxtime.nxtime.domain.ScheduleSlot;
import com.nxtime.nxtime.domain.ScheduleTemplate;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ScheduleIncidentResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.repository.ScheduleAssignmentRepository;
import com.nxtime.nxtime.repository.ScheduleIncidentRepository;
import com.nxtime.nxtime.repository.ScheduleSlotRepository;
import com.nxtime.nxtime.repository.ScheduleTemplateRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.JornadaTeoricaService;
import com.nxtime.nxtime.service.ScheduleIncidentService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El barrido de incidencias, contra PostgreSQL real (Fase B2).
 *
 * Una semana de cuadrante de oficina (09:00-17:00, de lunes a viernes) y lo
 * que se ficha cada día. Lo que se prueba aquí vive en la base: el agrupado
 * de fichajes por día en hora de España, la unicidad que hace el barrido
 * idempotente, y el cálculo por lotes del horario teórico.
 *
 * El cuadrante se inserta directamente por los repositorios, en una semana
 * pasada: el servicio de cuadrantes no deja asignar hacia atrás, y eso se
 * prueba en CuadranteIT. Aquí lo que interesa es lo que pasa DESPUÉS.
 *
 * <b>El barrido es global</b> —todas las empresas— y la base se comparte entre
 * los métodos de la clase, así que los cuadrantes que dejan otros tests en la
 * misma semana también producen incidencias. Por eso nada de aquí mira el
 * recuento total que devuelve {@code detectar}: se mira lo de la empresa o la
 * persona de cada test.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
@Import(IncidenciasIT.CapturaDeAvisos.class)
@DisplayName("Incidencias de cuadrante")
class IncidenciasIT {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate LUNES = LocalDate.of(2025, 3, 3);
    private static final LocalDate VIERNES = LUNES.plusDays(4);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "incidencias_it_" + System.nanoTime();
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

    @Autowired private ScheduleIncidentService incidentService;
    @Autowired private ScheduleIncidentRepository incidentRepository;
    @Autowired private JornadaTeoricaService jornadaTeoricaService;
    @Autowired private ScheduleTemplateRepository templateRepository;
    @Autowired private ScheduleSlotRepository slotRepository;
    @Autowired private ScheduleAssignmentRepository assignmentRepository;
    @Autowired private TimeEntryRepository timeEntryRepository;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CapturaDeAvisos capturas;

    private Company empresa;
    private User ana;
    private User gestor;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ana = persona(Role.EMPLEADO);
        gestor = persona(Role.GESTOR);
        capturas.detectadas.clear();
        capturas.resumenes.clear();
    }

    private User persona(Role rol) {
        return userRepository.save(User.builder()
                .nombre(rol == Role.GESTOR ? "Gestora" : "Ana").apellidos("Prueba")
                .email(rol.name().toLowerCase() + System.nanoTime() + "@test").contrasena("x")
                .rol(rol).empresa(empresa).activo(true).horasSemanales(new BigDecimal("40.0")).build());
    }

    /** Oficina de 09:00 a 17:00 de lunes a viernes, desde LUNES. */
    private void conCuadranteDeOficina(User persona) {
        ScheduleTemplate oficina = templateRepository.save(ScheduleTemplate.builder()
                .empresa(empresa).nombre("Oficina " + System.nanoTime()).build());
        for (short dia = 1; dia <= 5; dia++) {
            slotRepository.save(ScheduleSlot.builder().plantilla(oficina).diaSemana(dia).inicio(540).fin(1020).build());
        }
        assignmentRepository.save(ScheduleAssignment.builder()
                .empresa(empresa).usuario(persona).plantilla(oficina).fechaInicio(LUNES).build());
    }

    private TimeEntry fichar(User persona, LocalDate dia, String entrada, String salida) {
        return timeEntryRepository.save(TimeEntry.builder()
                .usuario(persona).empresa(empresa)
                .horaEntrada(ZonedDateTime.of(dia, LocalTime.parse(entrada), MADRID).toInstant())
                .horaSalida(ZonedDateTime.of(dia, LocalTime.parse(salida), MADRID).toInstant())
                .segundosPausaAcumulados(0).enPausa(false).build());
    }

    private List<NotificationEvents.ScheduleIncidentDetected> deEstaEmpresa(
            List<NotificationEvents.ScheduleIncidentDetected> eventos) {
        return eventos.stream().filter(evento -> evento.empresaId() == empresa.getId()).toList();
    }

    private List<ScheduleIncident> incidenciasDe(User persona) {
        return incidentRepository.findDeUsuarioEnRango(persona.getId(), LUNES, LUNES.plusDays(6));
    }

    /**
     * Lunes tarde, martes sin fichar, miércoles festivo, jueves sale pronto,
     * viernes perfecto. Una semana con todo.
     */
    private void semanaConDeTodo() {
        conCuadranteDeOficina(ana);
        holidayRepository.save(Holiday.builder()
                .empresa(empresa).fecha(LUNES.plusDays(2)).descripcion("Fiesta local").ambito(HolidayScope.LOCAL).build());
        fichar(ana, LUNES, "09:30", "17:00");
        fichar(ana, LUNES.plusDays(3), "09:00", "16:00");
        fichar(ana, VIERNES, "09:00", "17:00");
    }

    @TestConfiguration
    static class CapturaDeAvisos {
        final List<NotificationEvents.ScheduleIncidentDetected> detectadas = new ArrayList<>();
        final List<NotificationEvents.ScheduleIncidentSummary> resumenes = new ArrayList<>();

        @EventListener
        void detectada(NotificationEvents.ScheduleIncidentDetected evento) {
            detectadas.add(evento);
        }

        @EventListener
        void resumen(NotificationEvents.ScheduleIncidentSummary evento) {
            resumenes.add(evento);
        }
    }

    // ------------------------------------------------------------------
    // El barrido
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una semana con de todo: cada día sale lo que debe, y el festivo no es una ausencia")
    void unaSemanaConDeTodo() {
        semanaConDeTodo();

        incidentService.detectar(LUNES, LUNES.plusDays(6));

        assertThat(incidenciasDe(ana))
                .extracting(ScheduleIncident::getFecha, ScheduleIncident::getTipo, ScheduleIncident::getMinutos)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(LUNES, ScheduleIncidentType.RETRASO, 30),
                        org.assertj.core.groups.Tuple.tuple(LUNES.plusDays(1), ScheduleIncidentType.AUSENCIA, 480),
                        org.assertj.core.groups.Tuple.tuple(
                                LUNES.plusDays(3), ScheduleIncidentType.SALIDA_ANTICIPADA, 60));
    }

    /**
     * El barrido mira catorce días cada noche, así que pasa varias veces por el
     * mismo día. La segunda pasada no puede crear nada ni avisar otra vez.
     */
    @Test
    @DisplayName("Pasar dos veces por la misma semana no duplica nada ni vuelve a avisar")
    void idempotente() {
        semanaConDeTodo();
        incidentService.detectar(LUNES, LUNES.plusDays(6));
        capturas.detectadas.clear();
        capturas.resumenes.clear();

        incidentService.detectar(LUNES, LUNES.plusDays(6));
        assertThat(incidenciasDe(ana)).hasSize(3);
        assertThat(deEstaEmpresa(capturas.detectadas)).isEmpty();
        assertThat(capturas.resumenes).noneMatch(resumen -> resumen.empresaId() == empresa.getId());
    }

    /**
     * Una corrección aprobada después crea el fichaje que faltaba: la ausencia
     * pendiente deja de proceder y se retira. Una que alguien ya explicó, no:
     * sobre ella ya ha hablado una persona.
     */
    @Test
    @DisplayName("Si una corrección arregla el día, la pendiente se retira; la ya explicada, no")
    void retirarLoQueYaNoProcede() {
        semanaConDeTodo();
        incidentService.detectar(LUNES, LUNES.plusDays(6));
        ScheduleIncident retraso = incidenciasDe(ana).stream()
                .filter(i -> i.getTipo() == ScheduleIncidentType.RETRASO).findFirst().orElseThrow();
        incidentService.justificar(retraso.getId(), "Avería del metro", ana);

        // La corrección: el martes sí se trabajó, y el lunes se entró a su hora.
        fichar(ana, LUNES.plusDays(1), "09:00", "17:00");
        TimeEntry lunes = timeEntryRepository.findVivosDeUsuariosQueEmpiezanEntre(List.of(ana.getId()),
                ZonedDateTime.of(LUNES, LocalTime.MIDNIGHT, MADRID).toInstant(),
                ZonedDateTime.of(LUNES.plusDays(1), LocalTime.MIDNIGHT, MADRID).toInstant()).get(0);
        lunes.setAnulado(true);
        timeEntryRepository.save(lunes);
        fichar(ana, LUNES, "09:00", "17:00");

        incidentService.detectar(LUNES, LUNES.plusDays(6));

        assertThat(incidenciasDe(ana))
                .extracting(ScheduleIncident::getTipo)
                .as("la ausencia pendiente se retira; el retraso ya explicado se queda")
                .containsExactlyInAnyOrder(ScheduleIncidentType.RETRASO, ScheduleIncidentType.SALIDA_ANTICIPADA);
    }

    @Test
    @DisplayName("Una pendiente se pone al día si una corrección mueve la hora")
    void actualizarLaPendiente() {
        conCuadranteDeOficina(ana);
        TimeEntry tarde = fichar(ana, LUNES, "09:40", "17:00");
        incidentService.detectar(LUNES, LUNES);

        tarde.setAnulado(true);
        timeEntryRepository.save(tarde);
        fichar(ana, LUNES, "09:25", "17:00");
        incidentService.detectar(LUNES, LUNES);

        assertThat(incidenciasDe(ana)).singleElement()
                .satisfies(incidencia -> assertThat(incidencia.getMinutos()).isEqualTo(25));
    }

    /** Sin cuadrante no hay contra qué comparar: nadie recibe incidencias por no tenerlo. */
    @Test
    @DisplayName("Quien no tiene cuadrante no recibe nunca una incidencia")
    void sinCuadranteNada() {
        fichar(ana, LUNES, "11:00", "12:00");

        incidentService.detectar(LUNES, LUNES.plusDays(6));
        assertThat(incidenciasDe(ana)).isEmpty();
    }

    @Test
    @DisplayName("Una jornada que cerró el sistema no produce salida anticipada")
    void cerradaPorElSistema() {
        conCuadranteDeOficina(ana);
        TimeEntry olvidada = fichar(ana, LUNES, "09:00", "12:00");
        olvidada.setJornadaIncompleta(true);
        timeEntryRepository.save(olvidada);

        incidentService.detectar(LUNES, LUNES);

        assertThat(incidenciasDe(ana)).isEmpty();
    }

    /** El cálculo por lotes y el de uno en uno tienen que decir lo mismo. */
    @Test
    @DisplayName("El horario teórico por lotes coincide con el de una persona")
    void porLotesIgualQueUnoAUno() {
        semanaConDeTodo();
        User otra = persona(Role.EMPLEADO);

        for (LocalDate dia = LUNES; !dia.isAfter(LUNES.plusDays(6)); dia = dia.plusDays(1)) {
            var porLotes = jornadaTeoricaService.diaDeVarios(List.of(ana, otra), dia);
            assertThat(porLotes.get(ana.getId())).as(dia.toString()).isEqualTo(jornadaTeoricaService.dia(ana, dia));
            assertThat(porLotes.get(otra.getId())).as(dia.toString()).isEqualTo(jornadaTeoricaService.dia(otra, dia));
        }
    }

    // ------------------------------------------------------------------
    // Los avisos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A quien las tiene, UN aviso con sus incidencias nuevas; a quien revisa, un resumen por empresa")
    void avisos() {
        semanaConDeTodo();
        incidentService.detectar(LUNES, LUNES.plusDays(6));

        // Uno, no tres: la primera versión mandaba un correo por incidencia,
        // y ejecutándolo contra los datos de demo salieron veinte en una noche.
        assertThat(deEstaEmpresa(capturas.detectadas)).singleElement().satisfies(evento -> {
            assertThat(evento.destinatarios()).extracting(User::getId).containsExactly(ana.getId());
            assertThat(evento.incidencias())
                    .extracting(NotificationEvents.IncidenciaNueva::fecha, NotificationEvents.IncidenciaNueva::tipo)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(LUNES, ScheduleIncidentType.RETRASO),
                            org.assertj.core.groups.Tuple.tuple(LUNES.plusDays(1), ScheduleIncidentType.AUSENCIA),
                            org.assertj.core.groups.Tuple.tuple(LUNES.plusDays(3), ScheduleIncidentType.SALIDA_ANTICIPADA));
        });
        assertThat(capturas.resumenes)
                .filteredOn(resumen -> resumen.empresaId() == empresa.getId())
                .singleElement().satisfies(resumen -> {
            assertThat(resumen.incidenciasNuevas()).isEqualTo(3);
            assertThat(resumen.personas()).isEqualTo(1);
            assertThat(resumen.destinatarios()).extracting(User::getId).contains(gestor.getId());
        });
    }

    // ------------------------------------------------------------------
    // Explicar y decidir
    // ------------------------------------------------------------------

    private ScheduleIncident unaIncidencia() {
        conCuadranteDeOficina(ana);
        fichar(ana, LUNES, "09:30", "17:00");
        incidentService.detectar(LUNES, LUNES);
        return incidenciasDe(ana).get(0);
    }

    @Test
    @DisplayName("Quien la tiene la explica; alguien que no es ella decide")
    void explicarYDecidir() {
        ScheduleIncident incidencia = unaIncidencia();

        ScheduleIncidentResponse explicada = incidentService.justificar(incidencia.getId(), "Avería del metro", ana);
        assertThat(explicada.estado()).isEqualTo(ScheduleIncidentStatus.JUSTIFICADA);
        assertThat(incidentService.bandeja(gestor, false)).extracting(ScheduleIncidentResponse::id)
                .containsExactly(incidencia.getId());

        ScheduleIncidentResponse decidida = incidentService.resolver(incidencia.getId(), true, null, gestor);
        assertThat(decidida.estado()).isEqualTo(ScheduleIncidentStatus.ACEPTADA);
        assertThat(decidida.resueltaPor()).isEqualTo("Gestora");
        assertThat(incidentService.bandeja(gestor, false)).isEmpty();
        assertThat(incidentService.bandeja(gestor, true)).hasSize(1);
    }

    @Test
    @DisplayName("Nadie explica la de otro, ni decide sobre la suya, ni cambia una ya decidida")
    void conflictosDeInteres() {
        ScheduleIncident incidencia = unaIncidencia();

        assertThatThrownBy(() -> incidentService.justificar(incidencia.getId(), "Por ella", gestor))
                .isInstanceOf(TenantAccessException.class);

        // La gestora con su propia incidencia no la puede decidir.
        conCuadranteDeOficina(gestor);
        fichar(gestor, LUNES, "10:00", "17:00");
        incidentService.detectar(LUNES, LUNES);
        ScheduleIncident suya = incidenciasDe(gestor).get(0);
        assertThatThrownBy(() -> incidentService.resolver(suya.getId(), true, null, gestor))
                .isInstanceOf(TenantAccessException.class);
        assertThat(incidentService.bandeja(gestor, false)).extracting(ScheduleIncidentResponse::usuarioId)
                .as("la bandeja no le enseña las suyas").doesNotContain(gestor.getId());

        incidentService.resolver(incidencia.getId(), true, null, gestor);
        assertThatThrownBy(() -> incidentService.justificar(incidencia.getId(), "Tarde", ana))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> incidentService.resolver(incidencia.getId(), false, "No", gestor))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Rechazar pide motivo; aceptar, no")
    void rechazarPideMotivo() {
        ScheduleIncident incidencia = unaIncidencia();

        assertThatThrownBy(() -> incidentService.resolver(incidencia.getId(), false, "  ", gestor))
                .isInstanceOf(BusinessException.class);
        assertThat(incidentService.resolver(incidencia.getId(), false, "Tercera vez esta semana", gestor).estado())
                .isEqualTo(ScheduleIncidentStatus.RECHAZADA);
    }
}
