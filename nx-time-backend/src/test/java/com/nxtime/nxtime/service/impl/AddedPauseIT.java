package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.AddedPause;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.CorrectionStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AddPauseRequest;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.ResolveCorrectionRequest;
import com.nxtime.nxtime.repository.AddedPauseRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AddedPauseService;
import com.nxtime.nxtime.service.CorrectionService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Test de INTEGRACIÓN de las pausas añadidas a posteriori (ADR 015), contra
 * PostgreSQL real y con las transacciones de verdad.
 *
 * Existe por lo que un test con mocks no puede ver:
 *
 *  1. Que V19 y los permisos aguantan: el INSERT en {@code pausas_anadidas}
 *     lo hace el rol {@code nxtime_app}, y la traza con las acciones nuevas
 *     pasa por el CHECK de {@code auditoria_fichaje}.
 *  2. Que al aprobar una corrección las pausas se heredan <b>medido en el
 *     agregado SQL</b>, que es lo que usan informes y horas extra. El
 *     unitario prueba que el campo viaja; solo esto prueba que el neto no se
 *     infla.
 *  3. Que las filas del libro se mudan a la versión corregida.
 *
 * Requisito: {@code docker compose up -d postgres} (ver ApiContractTest).
 */
@SpringBootTest
class AddedPauseIT {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "added_pause_it_" + System.nanoTime();
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
    private AddedPauseService addedPauseService;
    @Autowired
    private CorrectionService correctionService;
    @Autowired
    private AddedPauseRepository addedPauseRepository;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    private Company empresa;
    private User empleada;
    private User gestor;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        empleada = crearUsuario("empleada" + System.nanoTime() + "@test", Role.EMPLEADO);
        gestor = crearUsuario("gestor" + System.nanoTime() + "@test", Role.GESTOR);
    }

    private User crearUsuario(String email, Role rol) {
        return userRepository.save(User.builder()
                .nombre("Nombre").apellidos("Apellidos").email(email).contrasena("x")
                .rol(rol).empresa(empresa).activo(true)
                .horasSemanales(new BigDecimal("40.0"))
                .build());
    }

    /** Jornada cerrada de 9:00 a 17:00, hora de Madrid, hace unos días. */
    private TimeEntry jornadaPasada(long segundosPausa) {
        LocalDate dia = LocalDate.now(MADRID).minusDays(3);
        Instant entrada = ZonedDateTime.of(dia, LocalTime.of(9, 0), MADRID).toInstant();
        return timeEntryRepository.save(TimeEntry.builder()
                .usuario(empleada).empresa(empresa)
                .horaEntrada(entrada).horaSalida(entrada.plus(8, ChronoUnit.HOURS))
                .segundosPausaAcumulados(segundosPausa)
                .build());
    }

    private long netoDeLaJornada(TimeEntry jornada) {
        Instant dia = jornada.getHoraEntrada().truncatedTo(ChronoUnit.DAYS);
        return timeEntryRepository.sumarSegundosTrabajados(
                empleada.getId(), dia.minus(1, ChronoUnit.DAYS), dia.plus(2, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("Jornada abierta: la pausa se aplica, queda en el libro y el contador baja el neto")
    void jornadaAbierta_seAplicaYQuedaEnElLibro() {
        // Abierta desde hace tres horas: siempre va directa, sea la hora que sea.
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        TimeEntry abierta = timeEntryRepository.save(TimeEntry.builder()
                .usuario(empleada).empresa(empresa)
                .horaEntrada(ahora.minus(3, ChronoUnit.HOURS))
                .build());

        AddedPauseService.Resultado resultado = addedPauseService.anadir(abierta.getId(), new AddPauseRequest(
                ahora.minus(2, ChronoUnit.HOURS), ahora.minus(1, ChronoUnit.HOURS), "Olvidé fichar la comida"),
                empleada);

        assertThat(resultado.aplicada()).isTrue();
        TimeEntry releida = timeEntryRepository.findById(abierta.getId()).orElseThrow();
        assertThat(releida.getSegundosPausaAcumulados()).isEqualTo(3600);

        List<AddedPause> libro = addedPauseRepository.findByRegistroAndAnuladaFalseOrderByInicioAsc(releida);
        assertThat(libro).singleElement().satisfies(p -> {
            assertThat(p.getMotivo()).isEqualTo("Olvidé fichar la comida");
            assertThat(p.getSolicitud()).isNull();
        });
    }

    @Test
    @DisplayName("Día pasado: queda pendiente, y al aprobarla la pausa entra en la versión corregida")
    void diaPasado_alAprobar_entraEnLaVersionCorregida() {
        TimeEntry pasada = jornadaPasada(0);
        long netoAntes = netoDeLaJornada(pasada);

        AddedPauseService.Resultado pedida = addedPauseService.anadir(pasada.getId(), new AddPauseRequest(
                pasada.getHoraEntrada().plus(4, ChronoUnit.HOURS),
                pasada.getHoraEntrada().plus(5, ChronoUnit.HOURS),
                "Olvidé fichar la comida"), empleada);

        assertThat(pedida.aplicada()).isFalse();
        assertThat(pedida.correccion().estado()).isEqualTo(CorrectionStatus.PENDIENTE);
        // Mientras nadie la aprueba, el neto no cambia.
        assertThat(netoDeLaJornada(pasada)).isEqualTo(netoAntes);

        CorrectionResponse aprobada = correctionService.resolver(
                pedida.correccion().id(), new ResolveCorrectionRequest(true, null), gestor);
        assertThat(aprobada.estado()).isEqualTo(CorrectionStatus.APROBADA);

        // Una hora menos de trabajo, medido en el agregado que usan los informes.
        assertThat(netoDeLaJornada(pasada)).isEqualTo(netoAntes - 3600);

        TimeEntry corregida = timeEntryRepository.findAll().stream()
                .filter(t -> t.getRegistroOriginal() != null && t.getRegistroOriginal().getId() == pasada.getId())
                .findFirst().orElseThrow();
        assertThat(addedPauseRepository.findByRegistroAndAnuladaFalseOrderByInicioAsc(corregida))
                .singleElement()
                .satisfies(p -> assertThat(p.getSolicitud()).isNotNull());
    }

    /*
     * La regresión del arreglo de #41, medida donde de verdad importa. Antes,
     * aprobar una corrección construía la versión nueva con 0 segundos de
     * pausa, y el neto de la jornada subía solo.
     */
    @Test
    @DisplayName("Aprobar una corrección que no toca las horas deja el neto exactamente igual")
    void corregirSinCambiarHoras_noInflaElNeto() {
        TimeEntry pasada = jornadaPasada(2700); // 45 min de pausa fichada
        long netoAntes = netoDeLaJornada(pasada);

        CorrectionResponse pedida = correctionService.solicitar(pasada.getId(), new CorrectionRequestDTO(
                pasada.getHoraEntrada(), pasada.getHoraSalida(), "Revisión sin cambios"), empleada);
        correctionService.resolver(pedida.id(), new ResolveCorrectionRequest(true, null), gestor);

        assertThat(netoDeLaJornada(pasada)).isEqualTo(netoAntes);
        assertThat(netoAntes).isEqualTo(Duration.ofHours(8).getSeconds() - 2700);
    }

    @Test
    @DisplayName("Una corrección de horas muda las pausas añadidas a la versión nueva")
    void corregirHoras_mudaLasPausasDelLibro() {
        TimeEntry pasada = jornadaPasada(3600);
        addedPauseRepository.save(AddedPause.builder()
                .empresa(empresa).registro(pasada)
                .inicio(pasada.getHoraEntrada().plus(4, ChronoUnit.HOURS))
                .fin(pasada.getHoraEntrada().plus(5, ChronoUnit.HOURS))
                .motivo("Comida").creadaPor(empleada)
                .build());

        CorrectionResponse pedida = correctionService.solicitar(pasada.getId(), new CorrectionRequestDTO(
                pasada.getHoraEntrada(), pasada.getHoraSalida().plus(30, ChronoUnit.MINUTES),
                "Salí media hora más tarde"), empleada);
        correctionService.resolver(pedida.id(), new ResolveCorrectionRequest(true, null), gestor);

        TimeEntry original = timeEntryRepository.findById(pasada.getId()).orElseThrow();
        assertThat(addedPauseRepository.findByRegistroAndAnuladaFalseOrderByInicioAsc(original)).isEmpty();

        TimeEntry corregida = timeEntryRepository.findAll().stream()
                .filter(t -> t.getRegistroOriginal() != null && t.getRegistroOriginal().getId() == pasada.getId())
                .findFirst().orElseThrow();
        assertThat(addedPauseRepository.findByRegistroAndAnuladaFalseOrderByInicioAsc(corregida)).hasSize(1);
        assertThat(corregida.getSegundosPausaAcumulados()).isEqualTo(3600);
    }
}
