package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.HolidayScope;
import com.nxtime.nxtime.domain.OvertimeStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.OvertimeAlertResponse;
import com.nxtime.nxtime.dto.OvertimeBalanceResponse;
import com.nxtime.nxtime.dto.ReviewOvertimeRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.repository.OvertimeAlertRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.OvertimeService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Test de INTEGRACIÓN del detector de horas extra (Fase F) contra
 * PostgreSQL real.
 *
 * Va contra base de datos y no con mocks porque lo que puede salir mal
 * aquí solo existe en la base de datos: el agregado por día es SQL
 * nativo con {@code AT TIME ZONE 'Europe/Madrid'}, y con un repositorio
 * simulado se estaría probando el mock. Los tres fallos que este test
 * existe para cazar —agrupar por el día equivocado, partir un turno de
 * noche en dos, y duplicar avisos en la segunda pasada— son todos de esa
 * clase.
 *
 * Requisito: {@code docker compose up -d postgres} (ver ApiContractTest).
 */
@SpringBootTest
@DisplayName("Detección y revisión de horas extra")
class OvertimeServiceIT {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "overtime_it_" + System.nanoTime();
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
    private OvertimeService overtimeService;
    @Autowired
    private OvertimeAlertRepository overtimeRepository;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private AbsenceRequestRepository absenceRepository;
    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    private Company empresa;
    private User empleado;
    private User gestor;

    /**
     * Una semana fija y pasada, no "la semana pasada" calculada desde
     * hoy: el umbral semanal solo mira semanas TERMINADAS, así que un
     * test relativo a hoy pasaría o fallaría según el día en que se
     * ejecute. Se ancla en un lunes de un año pasado y ya está.
     */
    private static final LocalDate LUNES = LocalDate.of(2025, 3, 3);

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        empleado = crearUsuario("empleado" + System.nanoTime() + "@test", Role.EMPLEADO);
        gestor = crearUsuario("gestor" + System.nanoTime() + "@test", Role.GESTOR);
    }

    private User crearUsuario(String email, Role rol) {
        return userRepository.save(User.builder()
                .nombre("Nombre")
                .apellidos("Apellidos")
                .email(email)
                .contrasena("x")
                .rol(rol)
                .empresa(empresa)
                .activo(true)
                .horasSemanales(new BigDecimal("40.0"))
                .build());
    }

    /** Una jornada de {@code horas} horas empezando a las 9:00 de ese día. */
    private TimeEntry fichar(User usuario, LocalDate dia, double horas) {
        return fichar(usuario, dia, LocalTime.of(9, 0), horas);
    }

    private TimeEntry fichar(User usuario, LocalDate dia, LocalTime entrada, double horas) {
        Instant horaEntrada = ZonedDateTime.of(dia, entrada, MADRID).toInstant();
        return timeEntryRepository.save(TimeEntry.builder()
                .usuario(usuario)
                .empresa(empresa)
                .horaEntrada(horaEntrada)
                .horaSalida(horaEntrada.plusSeconds((long) (horas * 3600)))
                .segundosPausaAcumulados(0)
                .enPausa(false)
                .build());
    }

    /** Cinco jornadas de ocho horas: la semana normal, sin excesos. */
    private void semanaNormal(User usuario) {
        for (int i = 0; i < 5; i++) {
            fichar(usuario, LUNES.plusDays(i), 8);
        }
    }

    private void detectarLaSemana() {
        overtimeService.detectar(LUNES, LUNES.plusDays(6));
    }

    private List<OvertimeAlertResponse> avisosDe(User usuario) {
        return overtimeService.mios(usuario, LUNES.getYear());
    }

    // ------------------------------------------------------------------
    // Umbral diario
    // ------------------------------------------------------------------

    @Test
    @DisplayName("una semana de jornadas normales no genera ningún aviso")
    void semanaNormalNoGeneraNada() {
        semanaNormal(empleado);
        detectarLaSemana();

        assertThat(avisosDe(empleado)).isEmpty();
    }

    @Test
    @DisplayName("una jornada de once horas genera un aviso diario de dos horas")
    void jornadaLarga() {
        fichar(empleado, LUNES, 11);
        detectarLaSemana();

        assertThat(avisosDe(empleado))
                .singleElement()
                .satisfies(aviso -> {
                    assertThat(aviso.tipo()).isEqualTo("DIARIA");
                    assertThat(aviso.fecha()).isEqualTo(LUNES);
                    assertThat(aviso.minutosExtra()).isEqualTo(120);
                    assertThat(aviso.minutosEsperados()).isEqualTo(540);
                    assertThat(aviso.estado()).isEqualTo("ABIERTO");
                    assertThat(aviso.registroId()).isNotNull();
                });
    }

    @Test
    @DisplayName("un turno partido SUMA: 5 h + 5 h son diez horas del mismo día")
    void turnoPartido() {
        // Es el caso que se escapa si se mira fichaje a fichaje en vez de
        // agrupar por día. Ninguna de las dos mitades llega a nueve
        // horas, pero juntas se pasan del art. 34.3 igual que una jornada
        // seguida de diez.
        fichar(empleado, LUNES, LocalTime.of(8, 0), 5);
        fichar(empleado, LUNES, LocalTime.of(16, 0), 5);
        detectarLaSemana();

        assertThat(avisosDe(empleado))
                .filteredOn(aviso -> "DIARIA".equals(aviso.tipo()))
                .singleElement()
                .satisfies(aviso -> assertThat(aviso.minutosExtra()).isEqualTo(60));
    }

    @Test
    @DisplayName("un turno de noche cuenta ENTERO en el día en que empieza")
    void turnoDeNoche() {
        // De 22:00 a 8:00 son diez horas del lunes, no dos del lunes y
        // ocho del martes. Partirlo por la medianoche dejaría dos medias
        // jornadas y ninguna se pasaría nunca del umbral. Además obliga a
        // que la agrupación proyecte a Madrid: en UTC, las 22:00 de un
        // día de verano ya son del día siguiente.
        fichar(empleado, LUNES, LocalTime.of(22, 0), 10);
        detectarLaSemana();

        assertThat(avisosDe(empleado))
                .filteredOn(aviso -> "DIARIA".equals(aviso.tipo()))
                .singleElement()
                .satisfies(aviso -> {
                    assertThat(aviso.fecha()).isEqualTo(LUNES);
                    assertThat(aviso.minutosExtra()).isEqualTo(60);
                });
    }

    @Test
    @DisplayName("las pausas se descuentan: nueve horas y media con una hora de comida no es exceso")
    void lasPausasNoCuentan() {
        TimeEntry jornada = fichar(empleado, LUNES, 9.5);
        jornada.setSegundosPausaAcumulados(3600);
        timeEntryRepository.save(jornada);

        detectarLaSemana();

        assertThat(avisosDe(empleado)).isEmpty();
    }

    @Test
    @DisplayName("una jornada anulada por una corrección no cuenta")
    void jornadaAnulada() {
        TimeEntry jornada = fichar(empleado, LUNES, 11);
        jornada.setAnulado(true);
        timeEntryRepository.save(jornada);

        detectarLaSemana();

        assertThat(avisosDe(empleado)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Umbral semanal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("45 h en una semana de cinco días son cinco horas de exceso")
    void semanaLarga() {
        for (int i = 0; i < 5; i++) {
            fichar(empleado, LUNES.plusDays(i), 9);
        }
        detectarLaSemana();

        assertThat(avisosDe(empleado))
                .filteredOn(aviso -> "SEMANAL".equals(aviso.tipo()))
                .singleElement()
                .satisfies(aviso -> {
                    assertThat(aviso.fecha()).isEqualTo(LUNES);
                    assertThat(aviso.fechaFin()).isEqualTo(LUNES.plusDays(6));
                    assertThat(aviso.minutosExtra()).isEqualTo(5 * 60);
                    assertThat(aviso.minutosEsperados()).isEqualTo(40 * 60);
                    assertThat(aviso.registroId()).isNull();
                });
    }

    @Test
    @DisplayName("con un festivo entre medias, el objetivo baja a 32 h y no salta nada")
    void semanaConFestivo() {
        // La razón de ser del prorrateo. Cuatro jornadas de ocho horas
        // son exactamente lo que se espera de esa semana; contra un
        // objetivo fijo de 40 h la persona parecería estar por debajo, y
        // cualquier día que se alargara un poco saltaría un aviso falso.
        holidayRepository.save(Holiday.builder()
                .empresa(empresa)
                .fecha(LUNES.plusDays(2))
                .descripcion("Festivo de prueba")
                .ambito(HolidayScope.LOCAL)
                .build());

        for (int i = 0; i < 5; i++) {
            if (i == 2) {
                continue; // el miércoles es festivo
            }
            fichar(empleado, LUNES.plusDays(i), 8);
        }
        detectarLaSemana();

        assertThat(avisosDe(empleado)).isEmpty();
    }

    @Test
    @DisplayName("en la semana del festivo, pasarse de las 32 h SÍ salta")
    void semanaConFestivoQueSePasa() {
        holidayRepository.save(Holiday.builder()
                .empresa(empresa)
                .fecha(LUNES.plusDays(2))
                .descripcion("Festivo de prueba")
                .ambito(HolidayScope.LOCAL)
                .build());

        for (int i = 0; i < 5; i++) {
            if (i == 2) {
                continue;
            }
            fichar(empleado, LUNES.plusDays(i), 8.75); // 35 h en cuatro días
        }
        detectarLaSemana();

        assertThat(avisosDe(empleado))
                .filteredOn(aviso -> "SEMANAL".equals(aviso.tipo()))
                .singleElement()
                .satisfies(aviso -> {
                    assertThat(aviso.minutosEsperados()).isEqualTo(32 * 60);
                    assertThat(aviso.minutosExtra()).isEqualTo(3 * 60);
                });
    }

    @Test
    @DisplayName("las ausencias APROBADAS también bajan el objetivo")
    void semanaConVacaciones() {
        absenceRepository.save(AbsenceRequest.builder()
                .usuario(empleado)
                .empresa(empresa)
                .tipo(AbsenceType.VACACIONES)
                .estado(AbsenceStatus.APROBADA)
                .fechaInicio(LUNES.plusDays(3))
                .fechaFin(LUNES.plusDays(4))
                .aprobadoPor(gestor)
                .fechaResolucion(Instant.now())
                .build());

        for (int i = 0; i < 3; i++) {
            fichar(empleado, LUNES.plusDays(i), 8); // 24 h en tres días hábiles
        }
        detectarLaSemana();

        assertThat(avisosDe(empleado)).isEmpty();
    }

    @Test
    @DisplayName("una ausencia PENDIENTE no baja el objetivo: todavía no es nada")
    void ausenciaPendienteNoCuenta() {
        // Si contara, bastaría con pedir unas vacaciones para que la
        // semana pareciera más corta de lo que es.
        absenceRepository.save(AbsenceRequest.builder()
                .usuario(empleado)
                .empresa(empresa)
                .tipo(AbsenceType.VACACIONES)
                .estado(AbsenceStatus.PENDIENTE)
                .fechaInicio(LUNES.plusDays(3))
                .fechaFin(LUNES.plusDays(4))
                .build());

        for (int i = 0; i < 3; i++) {
            fichar(empleado, LUNES.plusDays(i), 11.5); // 34,5 h en tres días
        }
        detectarLaSemana();

        // Contra el objetivo real de 40 h no hay exceso semanal, aunque
        // sí lo haya diario. Si la ausencia pendiente hubiera contado, el
        // objetivo habría bajado a 24 h y habría saltado un semanal.
        assertThat(avisosDe(empleado))
                .filteredOn(aviso -> "SEMANAL".equals(aviso.tipo()))
                .isEmpty();
    }

    @Test
    @DisplayName("una semana a medias no se evalúa hasta que termina")
    void semanaSinTerminar() {
        LocalDate hoy = LocalDate.now(MADRID);
        LocalDate lunesDeEstaSemana = hoy.with(DayOfWeek.MONDAY);
        // Diez horas hoy: exceso diario seguro. Si además saliera un
        // semanal, sería sobre una semana que aún no ha pasado.
        fichar(empleado, lunesDeEstaSemana, 10);

        overtimeService.detectar(lunesDeEstaSemana, hoy);

        assertThat(overtimeService.mios(empleado, hoy.getYear()))
                .filteredOn(aviso -> "SEMANAL".equals(aviso.tipo()))
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // Idempotencia
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pasar dos veces no duplica avisos")
    void esIdempotente() {
        // El proceso nocturno mira catorce días atrás cada noche, así que
        // repasa los mismos días trece veces. Sin esto, cada día largo
        // acabaría con trece avisos.
        fichar(empleado, LUNES, 11);
        detectarLaSemana();
        detectarLaSemana();
        detectarLaSemana();

        assertThat(avisosDe(empleado)).hasSize(1);
    }

    @Test
    @DisplayName("si el fichaje cambia, el aviso ABIERTO se actualiza")
    void actualizaLosAbiertos() {
        TimeEntry jornada = fichar(empleado, LUNES, 11);
        detectarLaSemana();
        assertThat(avisosDe(empleado)).singleElement()
                .satisfies(aviso -> assertThat(aviso.minutosExtra()).isEqualTo(120));

        // Una corrección de la Fase E recorta la jornada a diez horas.
        jornada.setHoraSalida(jornada.getHoraEntrada().plusSeconds(10 * 3600));
        timeEntryRepository.save(jornada);
        detectarLaSemana();

        assertThat(avisosDe(empleado)).singleElement()
                .satisfies(aviso -> assertThat(aviso.minutosExtra()).isEqualTo(60));
    }

    @Test
    @DisplayName("si el exceso desaparece, el aviso ABIERTO se retira")
    void retiraLosQueYaNoProceden() {
        TimeEntry jornada = fichar(empleado, LUNES, 11);
        detectarLaSemana();
        assertThat(avisosDe(empleado)).hasSize(1);

        // La corrección anula el fichaje. El día se queda sin jornadas
        // válidas y desaparece del agregado, así que el bucle normal no
        // vuelve a pasar por él: sin el paso de retirada, el aviso se
        // quedaría acusando de unas horas que ya no constan.
        jornada.setAnulado(true);
        timeEntryRepository.save(jornada);
        detectarLaSemana();

        assertThat(avisosDe(empleado)).isEmpty();
    }

    @Test
    @DisplayName("un aviso ya revisado NO lo toca el proceso nocturno")
    void noDeshaceDecisionesHumanas() {
        fichar(empleado, LUNES, 11);
        detectarLaSemana();
        long avisoId = avisosDe(empleado).get(0).id();

        overtimeService.revisar(avisoId, new ReviewOvertimeRequest(true, null), gestor);

        // Aunque el fichaje cambie después, la decisión manda.
        TimeEntry jornada = timeEntryRepository.findById(
                avisosDe(empleado).get(0).registroId()).orElseThrow();
        jornada.setAnulado(true);
        timeEntryRepository.save(jornada);
        detectarLaSemana();

        assertThat(overtimeRepository.findById(avisoId))
                .get()
                .satisfies(aviso -> {
                    assertThat(aviso.getEstado()).isEqualTo(OvertimeStatus.ACEPTADO);
                    assertThat(aviso.getMinutosExtra()).isEqualTo(120);
                });
    }

    // ------------------------------------------------------------------
    // Revisión
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nadie revisa sus propias horas extra, tenga el rol que tenga")
    void nadieRevisaLoSuyo() {
        // El gestor tiene "horasextra:revisar", así que el @PreAuthorize
        // del endpoint le deja pasar. Lo que lo para es el servicio, y
        // tiene que ser así: aceptar las horas extra que hiciste ayer es
        // cobrarlas, y justificarlas es hacerlas desaparecer.
        fichar(gestor, LUNES, 11);
        detectarLaSemana();
        long avisoId = overtimeService.mios(gestor, LUNES.getYear()).get(0).id();

        assertThatThrownBy(() ->
                overtimeService.revisar(avisoId, new ReviewOvertimeRequest(true, null), gestor))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("un empleado sin la authority no puede revisar")
    void empleadoNoRevisa() {
        User otro = crearUsuario("otro" + System.nanoTime() + "@test", Role.EMPLEADO);
        fichar(otro, LUNES, 11);
        detectarLaSemana();
        long avisoId = overtimeService.mios(otro, LUNES.getYear()).get(0).id();

        assertThatThrownBy(() ->
                overtimeService.revisar(avisoId, new ReviewOvertimeRequest(true, null), empleado))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("justificar sin explicación se rechaza")
    void justificarPideExplicacion() {
        // Aceptar no necesita motivo: el reloj ya ha medido. Decir que
        // once horas trabajadas NO cuentan sí -- es la decisión que un
        // inspector querría ver motivada.
        fichar(empleado, LUNES, 11);
        detectarLaSemana();
        long avisoId = avisosDe(empleado).get(0).id();

        assertThatThrownBy(() ->
                overtimeService.revisar(avisoId, new ReviewOvertimeRequest(false, "  "), gestor))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("un aviso ya revisado no se revisa dos veces")
    void noSeRevisaDosVeces() {
        fichar(empleado, LUNES, 11);
        detectarLaSemana();
        long avisoId = avisosDe(empleado).get(0).id();

        overtimeService.revisar(avisoId, new ReviewOvertimeRequest(true, null), gestor);

        assertThatThrownBy(() ->
                overtimeService.revisar(avisoId, new ReviewOvertimeRequest(false, "Cambio de idea"), gestor))
                .isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------------
    // Bolsa anual
    // ------------------------------------------------------------------

    @Test
    @DisplayName("solo lo ACEPTADO consume bolsa")
    void soloLoAceptadoConsumeBolsa() {
        fichar(empleado, LUNES, 11);         // 2 h de exceso diario
        fichar(empleado, LUNES.plusDays(1), 10); // 1 h más
        detectarLaSemana();

        List<OvertimeAlertResponse> diarios = avisosDe(empleado).stream()
                .filter(aviso -> "DIARIA".equals(aviso.tipo()))
                .toList();
        assertThat(diarios).hasSize(2);

        // Sin revisar, la bolsa está intacta: un exceso detectado todavía
        // no son horas extra.
        assertThat(overtimeService.bolsa(empleado, null, LUNES.getYear()).minutosConsumidos()).isZero();

        overtimeService.revisar(diarios.get(0).id(), new ReviewOvertimeRequest(true, null), gestor);
        overtimeService.revisar(diarios.get(1).id(),
                new ReviewOvertimeRequest(false, "Jornada intensiva pactada."), gestor);

        OvertimeBalanceResponse bolsa = overtimeService.bolsa(empleado, null, LUNES.getYear());
        assertThat(bolsa.minutosConsumidos()).isEqualTo(diarios.get(0).minutosExtra());
        assertThat(bolsa.minutosDisponibles())
                .isEqualTo(80 * 60 - diarios.get(0).minutosExtra());
        assertThat(bolsa.alLimite()).isFalse();
    }

    @Test
    @DisplayName("mirar la bolsa de otra persona pide permiso de revisión")
    void bolsaAjenaPidePermiso() {
        User otro = crearUsuario("otro" + System.nanoTime() + "@test", Role.EMPLEADO);

        assertThatThrownBy(() -> overtimeService.bolsa(empleado, otro.getId(), LUNES.getYear()))
                .isInstanceOf(TenantAccessException.class);

        assertThat(overtimeService.bolsa(gestor, otro.getId(), LUNES.getYear()).minutosConsumidos())
                .isZero();
    }

    @Test
    @DisplayName("un aviso de otra empresa no se puede revisar")
    void aislamientoEntreEmpresas() {
        fichar(empleado, LUNES, 11);
        detectarLaSemana();
        long avisoId = avisosDe(empleado).get(0).id();

        Company otraEmpresa = companyRepository.save(
                Company.builder().nombre("Otra " + System.nanoTime()).build());
        User gestorAjeno = userRepository.save(User.builder()
                .nombre("Ajeno").apellidos("Ajeno")
                .email("ajeno" + System.nanoTime() + "@test")
                .contrasena("x").rol(Role.RRHH).empresa(otraEmpresa).activo(true)
                .horasSemanales(new BigDecimal("40.0"))
                .build());

        assertThatThrownBy(() ->
                overtimeService.revisar(avisoId, new ReviewOvertimeRequest(true, null), gestorAjeno))
                .isInstanceOf(TenantAccessException.class);
    }
}
