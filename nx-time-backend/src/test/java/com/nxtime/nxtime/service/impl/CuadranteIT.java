package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.HolidayScope;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.ScheduleExceptionType;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.OvertimeAlertResponse;
import com.nxtime.nxtime.dto.ScheduleAssignmentRequest;
import com.nxtime.nxtime.dto.ScheduleAssignmentResponse;
import com.nxtime.nxtime.dto.ScheduleExceptionRequest;
import com.nxtime.nxtime.dto.ScheduleTemplateRequest;
import com.nxtime.nxtime.dto.ScheduleTemplateResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.GlobalExceptionHandler;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.repository.ScheduleAssignmentRepository;
import com.nxtime.nxtime.repository.ScheduleExceptionRepository;
import com.nxtime.nxtime.repository.ScheduleSlotRepository;
import com.nxtime.nxtime.repository.ScheduleTemplateRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.JornadaTeoricaService;
import com.nxtime.nxtime.service.JornadaTeoricaService.DiaTeorico;
import com.nxtime.nxtime.service.JornadaTeoricaService.Origen;
import com.nxtime.nxtime.service.NonWorkingDayService;
import com.nxtime.nxtime.service.OvertimeCalculator;
import com.nxtime.nxtime.service.OvertimeService;
import com.nxtime.nxtime.service.ScheduleService;
import com.nxtime.nxtime.service.WorkingDayService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cuadrantes y horario teórico, contra PostgreSQL real (Fase B1).
 *
 * Va contra base de datos porque lo que puede salir mal aquí vive en ella: los
 * EXCLUDE de V31, el orden en que Hibernate vuelca los DELETE y los INSERT al
 * reemplazar tramos, y el cálculo de horas extra, que agrega en SQL nativo.
 *
 * <b>El reloj del servicio se fija, por defecto, en el sábado 1 de marzo de
 * 2025.</b> Así se pueden asignar cuadrantes desde el lunes 3 <i>a través del
 * servicio</i> —sin saltarse la regla de no empezar en el pasado— y, a la vez,
 * esa semana ya está terminada para el barrido de horas extra, que usa el reloj
 * de verdad.
 *
 * El servicio con reloj es un bean de test y no un {@code new}: construido a
 * mano no tendría el proxy transaccional, y el borrado de tramos
 * ({@code @Modifying}) fallaría por falta de transacción. Es decir, el test
 * estaría probando algo distinto de lo que corre en producción.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
@Import({CuadranteIT.CapturaDeAvisos.class, CuadranteIT.ServicioConReloj.class})
@DisplayName("Cuadrantes y horario teórico")
class CuadranteIT {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate SABADO_ANTERIOR = LocalDate.of(2025, 3, 1);
    private static final LocalDate LUNES = LocalDate.of(2025, 3, 3);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "cuadrante_it_" + System.nanoTime();
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

    @Autowired private ScheduleTemplateRepository templateRepository;
    @Autowired private ScheduleSlotRepository slotRepository;
    @Autowired private ScheduleAssignmentRepository assignmentRepository;
    @Autowired private ScheduleExceptionRepository exceptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private AbsenceRequestRepository absenceRepository;
    @Autowired private TimeEntryRepository timeEntryRepository;
    @Autowired private JornadaTeoricaService jornadaTeoricaService;
    @Autowired private NonWorkingDayService nonWorkingDayService;
    @Autowired private WorkingDayService workingDayService;
    @Autowired private OvertimeService overtimeService;
    @Autowired private ScheduleService servicioConReloj;
    @Autowired private RelojMovible reloj;
    @Autowired private CapturaDeAvisos capturas;

    private Company empresa;
    private User empleado;
    private User gestor;
    private User rrhh;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        empleado = persona(Role.EMPLEADO, "40.0");
        gestor = persona(Role.GESTOR, "40.0");
        rrhh = persona(Role.RRHH, "40.0");
        capturas.avisos.clear();
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private User persona(Role rol, String horas) {
        return userRepository.save(User.builder()
                .nombre(rol.name().toLowerCase())
                .apellidos("Prueba")
                .email(rol.name().toLowerCase() + System.nanoTime() + "@test")
                .contrasena("x")
                .rol(rol)
                .empresa(empresa)
                .activo(true)
                .horasSemanales(new BigDecimal(horas))
                .build());
    }

    /** El servicio con "hoy" en la fecha que se diga, como si fuera ese día. */
    private ScheduleService servicioEl(LocalDate hoy) {
        reloj.hoyEs(hoy);
        return servicioConReloj;
    }

    private ScheduleService servicio() {
        return servicioEl(SABADO_ANTERIOR);
    }

    /** Un reloj que el test puede mover. */
    static final class RelojMovible extends Clock {
        private Instant ahora = Instant.now();

        void hoyEs(LocalDate dia) {
            ahora = dia.atTime(10, 0).toInstant(ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return Clock.fixed(ahora, zona);
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    @TestConfiguration
    static class ServicioConReloj {

        @Bean
        RelojMovible relojMovible() {
            return new RelojMovible();
        }

        /** El mismo servicio, con el reloj del test; Spring le pone su proxy transaccional. */
        @Bean
        @Primary
        ScheduleService servicioConReloj(
                ScheduleTemplateRepository templateRepository,
                ScheduleSlotRepository slotRepository,
                ScheduleAssignmentRepository assignmentRepository,
                ScheduleExceptionRepository exceptionRepository,
                UserRepository userRepository,
                JornadaTeoricaService jornadaTeoricaService,
                ApplicationEventPublisher eventPublisher,
                RelojMovible reloj) {
            return new ScheduleServiceImpl(templateRepository, slotRepository, assignmentRepository,
                    exceptionRepository, userRepository, jornadaTeoricaService, eventPublisher, reloj);
        }
    }

    /** Lunes a viernes, un mismo horario cada día. {@code "09:00-14:00"}. */
    private static List<ScheduleTemplateRequest.Tramo> deLunesAViernes(String... tramos) {
        List<ScheduleTemplateRequest.Tramo> lista = new ArrayList<>();
        for (int dia = 1; dia <= 5; dia++) {
            for (String tramo : tramos) {
                String[] partes = tramo.split("-");
                lista.add(new ScheduleTemplateRequest.Tramo(dia, minutos(partes[0]), minutos(partes[1])));
            }
        }
        return lista;
    }

    private static int minutos(String hora) {
        LocalTime t = LocalTime.parse(hora);
        return t.getHour() * 60 + t.getMinute();
    }

    private ScheduleTemplateResponse plantilla(String nombre, List<ScheduleTemplateRequest.Tramo> tramos) {
        return servicio().crearPlantilla(new ScheduleTemplateRequest(nombre, null, tramos), gestor);
    }

    private ScheduleAssignmentResponse asignar(User persona, ScheduleTemplateResponse plantilla, LocalDate desde) {
        return servicio().asignar(
                new ScheduleAssignmentRequest(persona.getId(), plantilla.id(), desde, null), gestor);
    }

    private void festivo(LocalDate fecha) {
        holidayRepository.save(Holiday.builder()
                .empresa(empresa).fecha(fecha).descripcion("Fiesta local").ambito(HolidayScope.LOCAL).build());
    }

    private void vacaciones(User persona, LocalDate desde, LocalDate hasta) {
        absenceRepository.save(AbsenceRequest.builder()
                .usuario(persona).empresa(empresa).tipo(AbsenceType.VACACIONES).estado(AbsenceStatus.APROBADA)
                .fechaInicio(desde).fechaFin(hasta).aprobadoPor(gestor).fechaResolucion(Instant.now())
                .build());
    }

    private void fichar(User persona, LocalDate dia, double horas) {
        Instant entrada = ZonedDateTime.of(dia, LocalTime.of(9, 0), MADRID).toInstant();
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(persona).empresa(empresa).horaEntrada(entrada)
                .horaSalida(entrada.plusSeconds((long) (horas * 3600)))
                .segundosPausaAcumulados(0).enPausa(false).build());
    }

    @TestConfiguration
    static class CapturaDeAvisos {
        final List<NotificationEvents.ScheduleDiffersFromContract> avisos = new ArrayList<>();

        @EventListener
        void capturar(NotificationEvents.ScheduleDiffersFromContract evento) {
            avisos.add(evento);
        }
    }

    // ------------------------------------------------------------------
    // Cero regresión
    // ------------------------------------------------------------------

    /**
     * El requisito que hace que la fase no le cambie nada a nadie.
     *
     * Para una persona sin cuadrante, la semana teórica tiene que ser
     * exactamente la de siempre: la jornada contratada prorrateada por los
     * días hábiles (de lunes a viernes, sin festivos) menos las ausencias
     * aprobadas. Se calcula aquí por el camino viejo, con las mismas piezas
     * que usa el barrido de horas extra, y se compara.
     */
    @Test
    @DisplayName("Sin cuadrante, la semana teórica es exactamente la de siempre")
    void sinCuadranteLaSemanaEsLaDeSiempre() {
        festivo(LUNES.plusDays(2));
        vacaciones(empleado, LUNES.plusDays(3), LUNES.plusDays(3));

        Set<LocalDate> habiles = new LinkedHashSet<>(
                workingDayService.diasHabiles(empresa, LUNES, LUNES.plusDays(6)));
        habiles.remove(LUNES.plusDays(3));
        long deSiempre = OvertimeCalculator.objetivoSemanal(empleado.getHorasSemanales(), habiles.size());

        assertThat(deSiempre).as("tres días hábiles de 8 h").isEqualTo(3 * 8 * 60);
        assertThat(jornadaTeoricaService.minutosTeoricosSemana(empleado, LUNES)).isEqualTo(deSiempre);
    }

    @Test
    @DisplayName("Sin cuadrante, cada día es SIN_CUADRANTE y no inventa horario")
    void sinCuadranteNoInventaHorario() {
        assertThat(jornadaTeoricaService.dias(empleado, LUNES, LUNES.plusDays(6)))
                .allSatisfy(dia -> {
                    assertThat(dia.origen()).isEqualTo(Origen.SIN_CUADRANTE);
                    assertThat(dia.minutos()).isZero();
                    assertThat(dia.entrada()).isEmpty();
                });
    }

    /**
     * El barrido de horas extra, lado a lado: la misma semana trabajada,
     * dos personas, una con cuadrante de 30 h y otra sin él. Con 37,5 h
     * trabajadas, quien tiene cuadrante se pasa 7,5 h de lo previsto;
     * quien no, está por debajo de sus 40 h contratadas y no genera nada.
     */
    @Test
    @DisplayName("Las horas extra de quien tiene cuadrante se miden contra el cuadrante, y las de quien no, como siempre")
    void horasExtraLadoALado() {
        User conCuadrante = empleado;
        User sinCuadrante = persona(Role.EMPLEADO, "40.0");
        asignar(conCuadrante, plantilla("Seis horas", deLunesAViernes("09:00-15:00")), LUNES);

        for (int i = 0; i < 5; i++) {
            fichar(conCuadrante, LUNES.plusDays(i), 7.5);
            fichar(sinCuadrante, LUNES.plusDays(i), 7.5);
        }
        overtimeService.detectar(LUNES, LUNES.plusDays(6));

        assertThat(overtimeService.mios(conCuadrante, 2025))
                .filteredOn(aviso -> "SEMANAL".equals(aviso.tipo()))
                .singleElement()
                .satisfies(aviso -> {
                    assertThat(aviso.minutosEsperados()).isEqualTo(30 * 60);
                    assertThat(aviso.minutosExtra()).isEqualTo(450);
                });
        assertThat(overtimeService.mios(sinCuadrante, 2025))
                .extracting(OvertimeAlertResponse::tipo)
                .doesNotContain("SEMANAL");
    }

    // ------------------------------------------------------------------
    // Precedencia
    // ------------------------------------------------------------------

    /**
     * Una semana con todo: plantilla de jornada partida, un día libre pactado,
     * un festivo, unas vacaciones, un día con horario distinto y el fin de
     * semana libre. Cada día tiene que salir de donde debe.
     */
    @Test
    @DisplayName("Festivo y ausencia mandan sobre la excepción, y la excepción sobre la plantilla")
    void precedencia() {
        asignar(empleado, plantilla("Partida", deLunesAViernes("09:00-14:00", "15:00-18:00")), LUNES);
        festivo(LUNES.plusDays(2));
        vacaciones(empleado, LUNES.plusDays(3), LUNES.plusDays(3));
        servicio().crearExcepcion(new ScheduleExceptionRequest(
                empleado.getId(), LUNES.plusDays(1), ScheduleExceptionType.LIBRE, null, "Cambio de día"), gestor);
        servicio().crearExcepcion(new ScheduleExceptionRequest(
                empleado.getId(), LUNES.plusDays(4), ScheduleExceptionType.TRAMO,
                List.of(new ScheduleExceptionRequest.Tramo(minutos("08:00"), minutos("12:00"))), null), gestor);
        // Una excepción en un día que también es festivo: el festivo manda.
        servicio().crearExcepcion(new ScheduleExceptionRequest(
                empleado.getId(), LUNES.plusDays(2), ScheduleExceptionType.TRAMO,
                List.of(new ScheduleExceptionRequest.Tramo(minutos("10:00"), minutos("12:00"))), null), gestor);

        List<DiaTeorico> dias = jornadaTeoricaService.dias(empleado, LUNES.minusDays(1), LUNES.plusDays(6));

        assertThat(dias.get(0).origen()).as("domingo anterior, antes del cuadrante").isEqualTo(Origen.SIN_CUADRANTE);
        assertThat(dias.get(1)).satisfies(lunes -> {
            assertThat(lunes.origen()).isEqualTo(Origen.CUADRANTE);
            assertThat(lunes.minutos()).isEqualTo(8 * 60);
            assertThat(lunes.entrada()).contains(LocalTime.of(9, 0));
            assertThat(lunes.plantilla()).isEqualTo("Partida");
        });
        assertThat(dias.get(2)).satisfies(martes -> {
            assertThat(martes.origen()).isEqualTo(Origen.EXCEPCION);
            assertThat(martes.minutos()).isZero();
            assertThat(martes.motivo()).isEqualTo("Cambio de día");
        });
        assertThat(dias.get(3)).satisfies(miercoles -> {
            assertThat(miercoles.origen()).isEqualTo(Origen.NO_LABORABLE);
            assertThat(miercoles.motivo()).contains("Fiesta local");
        });
        assertThat(dias.get(4).origen()).as("jueves de vacaciones").isEqualTo(Origen.NO_LABORABLE);
        assertThat(dias.get(5)).satisfies(viernes -> {
            assertThat(viernes.origen()).isEqualTo(Origen.EXCEPCION);
            assertThat(viernes.minutos()).isEqualTo(4 * 60);
            assertThat(viernes.entrada()).contains(LocalTime.of(8, 0));
        });
        assertThat(dias.get(6)).satisfies(sabado -> {
            assertThat(sabado.origen()).isEqualTo(Origen.CUADRANTE);
            assertThat(sabado.minutos()).as("libre en la plantilla").isZero();
        });

        assertThat(jornadaTeoricaService.minutosTeoricosSemana(empleado, LUNES)).isEqualTo(8 * 60 + 4 * 60);
    }

    @Test
    @DisplayName("El turno de noche cuenta entero en el día en que empieza")
    void turnoDeNoche() {
        asignar(empleado, plantilla("Noche", List.of(
                new ScheduleTemplateRequest.Tramo(1, minutos("22:00"), minutos("06:00") + 1440))), LUNES);

        DiaTeorico lunes = jornadaTeoricaService.dia(empleado, LUNES);

        assertThat(lunes.minutos()).isEqualTo(8 * 60);
        assertThat(lunes.tramos()).singleElement().satisfies(tramo -> {
            assertThat(tramo.cruzaMedianoche()).isTrue();
            assertThat(tramo.horaFin()).isEqualTo(LocalTime.of(6, 0));
        });
        assertThat(jornadaTeoricaService.dia(empleado, LUNES.plusDays(1)).minutos())
                .as("el martes no hereda la madrugada del lunes").isZero();
    }

    /** La versión por rango y la de un día tienen que decir lo mismo, palabra por palabra. */
    @Test
    @DisplayName("Los motivos de no laborable por rango coinciden con los de día a día")
    void motivosPorRangoYPorDia() {
        festivo(LUNES.plusDays(2));
        vacaciones(empleado, LUNES.plusDays(3), LUNES.plusDays(4));

        var porRango = nonWorkingDayService.motivosEnRango(empleado, LUNES, LUNES.plusDays(6));
        for (LocalDate dia = LUNES; !dia.isAfter(LUNES.plusDays(6)); dia = dia.plusDays(1)) {
            assertThat(porRango.get(dia)).as(dia.toString())
                    .isEqualTo(nonWorkingDayService.motivo(empleado, dia).orElse(null));
        }
        assertThat(porRango).hasSize(3);
    }

    // ------------------------------------------------------------------
    // No reescribir el pasado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un cuadrante no puede empezar en el pasado")
    void noEmpiezaEnElPasado() {
        ScheduleTemplateResponse oficina = plantilla("Oficina", deLunesAViernes("09:00-17:00"));

        assertThatThrownBy(() -> servicioEl(LUNES).asignar(
                new ScheduleAssignmentRequest(empleado.getId(), oficina.id(), LUNES.minusDays(1), null), gestor))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /**
     * Cambiar los tramos de una plantilla que ya se aplicó cambiaría el
     * horario teórico de esos días sin que nadie los tocara. Renombrarla sí se
     * puede: no cambia ninguna hora.
     */
    @Test
    @DisplayName("Los tramos de una plantilla ya aplicada al pasado no se cambian; el nombre sí")
    void plantillaAplicadaAlPasado() {
        ScheduleTemplateResponse oficina = plantilla("Oficina", deLunesAViernes("09:00-17:00"));
        asignar(empleado, oficina, LUNES);
        ScheduleService diezDiasDespues = servicioEl(LUNES.plusDays(10));

        assertThatThrownBy(() -> diezDiasDespues.editarPlantilla(oficina.id(),
                new ScheduleTemplateRequest("Oficina", null, deLunesAViernes("08:00-16:00")), gestor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reescribiría");

        ScheduleTemplateResponse renombrada = diezDiasDespues.editarPlantilla(oficina.id(),
                new ScheduleTemplateRequest("Oficina central", null, deLunesAViernes("09:00-17:00")), gestor);
        assertThat(renombrada.nombre()).isEqualTo("Oficina central");
        assertThat(renombrada.tramosEditables()).isFalse();
        assertThat(jornadaTeoricaService.dia(empleado, LUNES).minutos()).isEqualTo(8 * 60);
    }

    @Test
    @DisplayName("Un cuadrante se cierra desde ayer en adelante, nunca antes")
    void cerrarNoReescribe() {
        ScheduleAssignmentResponse asignacion =
                asignar(empleado, plantilla("Oficina", deLunesAViernes("09:00-17:00")), LUNES);
        ScheduleService elMiercoles = servicioEl(LUNES.plusDays(2));

        assertThatThrownBy(() -> elMiercoles.cerrarAsignacion(asignacion.id(), LUNES, gestor))
                .isInstanceOf(BusinessException.class);

        assertThat(elMiercoles.cerrarAsignacion(asignacion.id(), LUNES.plusDays(1), gestor).fechaFin())
                .isEqualTo(LUNES.plusDays(1));
        assertThat(jornadaTeoricaService.dia(empleado, LUNES.plusDays(1)).origen()).isEqualTo(Origen.CUADRANTE);
        assertThat(jornadaTeoricaService.dia(empleado, LUNES.plusDays(2)).origen()).isEqualTo(Origen.SIN_CUADRANTE);
    }

    @Test
    @DisplayName("Una asignación que ya estuvo en vigor no se borra: se cierra")
    void noSeBorraLoQueYaAplico() {
        ScheduleAssignmentResponse asignacion =
                asignar(empleado, plantilla("Oficina", deLunesAViernes("09:00-17:00")), LUNES);

        assertThatThrownBy(() -> servicioEl(LUNES.plusDays(1)).borrarAsignacion(asignacion.id(), gestor))
                .isInstanceOf(BusinessException.class);

        servicio().borrarAsignacion(asignacion.id(), gestor);
        assertThat(assignmentRepository.findById(asignacion.id())).isEmpty();
    }

    @Test
    @DisplayName("Una excepción no se pone en un día pasado")
    void excepcionEnElPasado() {
        asignar(empleado, plantilla("Oficina", deLunesAViernes("09:00-17:00")), LUNES);

        assertThatThrownBy(() -> servicioEl(LUNES.plusDays(2)).crearExcepcion(new ScheduleExceptionRequest(
                empleado.getId(), LUNES.plusDays(1), ScheduleExceptionType.LIBRE, null, null), gestor))
                .isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------------
    // Lo que impone la base
    // ------------------------------------------------------------------

    /**
     * Reemplazar tramos que se pisan con los viejos: 09-14 por 10-15.
     *
     * Borrando entidad a entidad (deleteAll, o una colección con
     * orphanRemoval), Hibernate vuelca los INSERT antes que los DELETE, y el
     * tramo nuevo chocaría con el viejo contra el EXCLUDE de V31. Por eso el
     * servicio borra con una sentencia DELETE, que se ejecuta en el acto.
     * Comprobado: cambiando esa sentencia por deleteAll(), este test falla con
     * una DataIntegrityViolationException.
     */
    @Test
    @DisplayName("Cambiar los tramos de una plantilla sin usar funciona aunque los nuevos pisen a los viejos")
    void reemplazarTramosQueSePisan() {
        ScheduleTemplateResponse plantilla = plantilla("Mañanas", deLunesAViernes("09:00-14:00"));

        ScheduleTemplateResponse editada = servicio().editarPlantilla(plantilla.id(),
                new ScheduleTemplateRequest("Mañanas", null, deLunesAViernes("10:00-15:00")), gestor);

        assertThat(editada.tramos()).hasSize(5).allSatisfy(tramo -> {
            assertThat(tramo.horaInicio()).isEqualTo("10:00");
            assertThat(tramo.horaFin()).isEqualTo("15:00");
        });
        assertThat(slotRepository.findByPlantilla_IdOrderByDiaSemanaAscInicioAsc(plantilla.id())).hasSize(5);
    }

    @Test
    @DisplayName("Dos cuadrantes para la misma persona el mismo día los para la base")
    void dosCuadrantesAlMismoTiempo() {
        asignar(empleado, plantilla("Oficina", deLunesAViernes("09:00-17:00")), LUNES);
        ScheduleTemplateResponse otra = plantilla("Tardes", deLunesAViernes("15:00-21:00"));

        assertThatThrownBy(() -> asignar(empleado, otra, LUNES.plusDays(7)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_asignaciones_horario_sin_solape")
                .satisfies(CuadranteIT::contestaConSuPropioMensaje);
    }

    /**
     * La excepción REAL que lanza PostgreSQL, pasada por el manejador global.
     *
     * Existe porque el test unitario del manejador construía la excepción con
     * el nombre de la restricción ya puesto, y así no podía ver lo que pasaba
     * de verdad: Hibernate no sabe sacar ese nombre de un EXCLUDE, y los 409
     * salían con el mensaje genérico. Se descubrió levantando el backend y
     * haciendo peticiones, no con la suite.
     */
    private static void contestaConSuPropioMensaje(Throwable excepcion) {
        ProblemDetail problema = new GlobalExceptionHandler()
                .handleDataIntegrity((DataIntegrityViolationException) excepcion);
        assertThat(problema.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problema.getDetail()).doesNotStartWith("Eso choca con algo que ya existe");
    }

    @Test
    @DisplayName("Un día libre y un tramo el mismo día los para la base")
    void libreYTramoElMismoDia() {
        asignar(empleado, plantilla("Oficina", deLunesAViernes("09:00-17:00")), LUNES);
        servicio().crearExcepcion(new ScheduleExceptionRequest(
                empleado.getId(), LUNES, ScheduleExceptionType.LIBRE, null, null), gestor);

        assertThatThrownBy(() -> servicio().crearExcepcion(new ScheduleExceptionRequest(
                empleado.getId(), LUNES, ScheduleExceptionType.TRAMO,
                List.of(new ScheduleExceptionRequest.Tramo(600, 700)), null), gestor))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_excepciones_horario_sin_solape")
                .satisfies(CuadranteIT::contestaConSuPropioMensaje);
    }

    @Test
    @DisplayName("Sin cuadrante ese día no hay nada que exceptuar")
    void excepcionSinCuadrante() {
        assertThatThrownBy(() -> servicio().crearExcepcion(new ScheduleExceptionRequest(
                empleado.getId(), LUNES, ScheduleExceptionType.LIBRE, null, null), gestor))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Una plantilla asignada no se borra")
    void plantillaAsignadaNoSeBorra() {
        ScheduleTemplateResponse oficina = plantilla("Oficina", deLunesAViernes("09:00-17:00"));
        asignar(empleado, oficina, LUNES);

        assertThatThrownBy(() -> servicio().borrarPlantilla(oficina.id(), gestor))
                .isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------------
    // El aviso de jornada
    // ------------------------------------------------------------------

    /**
     * Un gestor pone el cuándo; RRHH fija el cuánto. Si el cuadrante que
     * asigna el primero no suma lo que fijó el segundo, se asigna igual —un
     * turno que rota puede cuadrar al mes y no a la semana—, pero se le dice
     * a quien asigna y se avisa a quien lleva los contratos.
     */
    @Test
    @DisplayName("Un cuadrante que no suma la jornada se asigna, se dice, y se avisa a RRHH")
    void cuadranteQueNoSumaLaJornada() {
        ScheduleAssignmentResponse respuesta =
                asignar(empleado, plantilla("Seis horas", deLunesAViernes("09:00-15:00")), LUNES);

        assertThat(respuesta.aviso()).contains("30 h").contains("40 h");
        assertThat(capturas.avisos).singleElement().satisfies(evento -> {
            assertThat(evento.minutosPlantilla()).isEqualTo(30 * 60);
            assertThat(evento.minutosContrato()).isEqualTo(40 * 60);
            assertThat(evento.destinatarios()).extracting(User::getId).contains(rrhh.getId());
            assertThat(evento.destinatarios()).extracting(User::getId).doesNotContain(gestor.getId());
        });
    }

    @Test
    @DisplayName("Un cuadrante que sí suma la jornada no avisa a nadie")
    void cuadranteQueSumaLaJornada() {
        ScheduleAssignmentResponse respuesta =
                asignar(empleado, plantilla("Ocho horas", deLunesAViernes("09:00-17:00")), LUNES);

        assertThat(respuesta.aviso()).isNull();
        assertThat(capturas.avisos).isEmpty();
    }

    // ------------------------------------------------------------------
    // Multiempresa
    // ------------------------------------------------------------------

    @Test
    @DisplayName("No se puede asignar la plantilla de otra empresa ni a alguien de otra empresa")
    void aislamiento() {
        Company otra = companyRepository.save(Company.builder().nombre("Otra " + System.nanoTime()).build());
        User gestorDeLaOtra = userRepository.save(User.builder()
                .nombre("Otro").email("otro" + System.nanoTime() + "@test").contrasena("x")
                .rol(Role.GESTOR).empresa(otra).activo(true).build());
        ScheduleTemplateResponse deLaOtra = servicio().crearPlantilla(
                new ScheduleTemplateRequest("Suya", null, deLunesAViernes("09:00-17:00")), gestorDeLaOtra);

        assertThatThrownBy(() -> asignar(empleado, deLaOtra, LUNES)).isInstanceOf(TenantAccessException.class);
    }
}
