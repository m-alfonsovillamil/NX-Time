package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.MonthlySignature;
import com.nxtime.nxtime.domain.MonthlySignatureStatus;
import com.nxtime.nxtime.domain.Notice;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AddPauseRequest;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.MonthlySignatureResponse;
import com.nxtime.nxtime.dto.ResolveCorrectionRequest;
import com.nxtime.nxtime.dto.SignableMonthResponse;
import com.nxtime.nxtime.dto.TeamSignatureResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.report.MonthlyReport;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.MonthlySignatureRepository;
import com.nxtime.nxtime.repository.NoticeRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AddedPauseService;
import com.nxtime.nxtime.service.CorrectionService;
import com.nxtime.nxtime.service.MonthlySignatureService;
import com.nxtime.nxtime.service.ReportService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La firma mensual contra PostgreSQL real y con las transacciones de verdad
 * (Fase B3, ADR 025).
 *
 * Lo que importa de verdad está en el bloque de la invalidación: que una
 * corrección <b>aprobada por el camino de siempre</b> (pedir, aprobar,
 * aplicar) deja la firma sin efecto en la misma transacción, sin que ese
 * camino sepa nada de firmas; y que una corrección que no cambia lo firmado
 * (anular el fichaje y recrearlo igual) NO la tumba. Ninguna de las dos cosas
 * se ve con mocks: dependen del evento de auditoría, de la fase BEFORE_COMMIT y
 * de lo que la consulta ve de la transacción en curso.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
@Import(FirmaMensualIT.CapturaDeAvisos.class)
@DisplayName("Firma mensual")
class FirmaMensualIT {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Un mes pasado cualquiera: firmar no depende de qué día se ejecute el test. */
    private static final YearMonth MARZO = YearMonth.of(2025, 3);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "firma_mensual_it_" + System.nanoTime();
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

    @Autowired private MonthlySignatureService signatureService;
    @Autowired private MonthlySignatureRepository signatureRepository;
    @Autowired private AddedPauseService addedPauseService;
    @Autowired private CorrectionService correctionService;
    @Autowired private ReportService reportService;
    @Autowired private TimeEntryRepository timeEntryRepository;
    @Autowired private NoticeRepository noticeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CapturaDeAvisos capturas;

    private Company empresa;
    private User ana;
    private User gestor;
    private User rrhh;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ana = persona(Role.EMPLEADO, "Ana");
        gestor = persona(Role.GESTOR, "Gestora");
        rrhh = persona(Role.RRHH, "Elena");
        capturas.invalidadas.clear();
        capturas.recordatorios.clear();
    }

    private User persona(Role rol, String nombre) {
        return userRepository.save(User.builder()
                .nombre(nombre).apellidos("Prueba")
                .email(rol.name().toLowerCase() + System.nanoTime() + "@test").contrasena("x")
                .rol(rol).empresa(empresa).activo(true).horasSemanales(new BigDecimal("40.0")).build());
    }

    /** Una jornada cerrada de 9:00 a 17:00 del día del mes que se diga. */
    private TimeEntry jornada(User persona, YearMonth mes, int dia) {
        Instant entrada = ZonedDateTime.of(mes.atDay(dia), LocalTime.of(9, 0), MADRID).toInstant();
        return timeEntryRepository.save(TimeEntry.builder()
                .usuario(persona).empresa(empresa)
                .horaEntrada(entrada).horaSalida(entrada.plus(8, ChronoUnit.HOURS))
                .build());
    }

    private MonthlySignature vigenteDe(User persona, YearMonth mes) {
        return signatureRepository.findByUsuario_IdAndAnioAndMesAndEstado(
                persona.getId(), mes.getYear(), mes.getMonthValue(), MonthlySignatureStatus.VIGENTE).orElse(null);
    }

    private static HttpStatus estadoDe(Throwable fallo) {
        return ((BusinessException) fallo).getStatus();
    }

    // ------------------------------------------------------------------
    // Firmar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Firmar un mes guarda su huella, sus jornadas y su neto, y la comprobación cuadra")
    void firmar() {
        jornada(ana, MARZO, 3);
        jornada(ana, MARZO, 4);

        MonthlySignatureResponse firma = signatureService.firmar(ana, MARZO, "10.0.0.1");

        assertThat(firma.estado()).isEqualTo(MonthlySignatureStatus.VIGENTE);
        assertThat(firma.hash()).hasSize(64);
        assertThat(firma.jornadas()).isEqualTo(2);
        assertThat(firma.segundosNetos()).isEqualTo(2 * 8 * 3600);
        assertThat(signatureService.verificar(firma.id(), ana).coincide()).isTrue();
    }

    @Test
    @DisplayName("No se firma un mes sin terminar, dos veces, ni con una jornada abierta, cerrada por el sistema o ninguna")
    void reglasDeFirma() {
        YearMonth esteMes = YearMonth.now(MADRID);
        assertThatThrownBy(() -> signatureService.firmar(ana, esteMes, null))
                .satisfies(fallo -> assertThat(estadoDe(fallo)).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> signatureService.firmar(ana, MARZO, null))
                .hasMessageContaining("No hay jornadas")
                .satisfies(fallo -> assertThat(estadoDe(fallo)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        TimeEntry abierta = timeEntryRepository.save(TimeEntry.builder().usuario(ana).empresa(empresa)
                .horaEntrada(ZonedDateTime.of(MARZO.atDay(5), LocalTime.of(9, 0), MADRID).toInstant()).build());
        assertThatThrownBy(() -> signatureService.firmar(ana, MARZO, null))
                .hasMessageContaining("sin cerrar")
                .satisfies(fallo -> assertThat(estadoDe(fallo)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        abierta.setHoraSalida(abierta.getHoraEntrada().plus(8, ChronoUnit.HOURS));
        abierta.setJornadaIncompleta(true);
        abierta = timeEntryRepository.save(abierta);
        assertThatThrownBy(() -> signatureService.firmar(ana, MARZO, null))
                .hasMessageContaining("la cerró el sistema")
                .satisfies(fallo -> assertThat(estadoDe(fallo)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        abierta.setJornadaIncompleta(false);
        abierta = timeEntryRepository.save(abierta);
        signatureService.firmar(ana, MARZO, null);
        assertThatThrownBy(() -> signatureService.firmar(ana, MARZO, null))
                .satisfies(fallo -> assertThat(estadoDe(fallo)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("Dos firmas vigentes del mismo mes las para el índice parcial, aunque el servicio no mirara")
    void dosVigentes_lasParaLaBase() {
        MonthlySignature.MonthlySignatureBuilder firma = MonthlySignature.builder()
                .empresa(empresa).usuario(ana).anio(2025).mes(3).hash("a".repeat(64))
                .versionHuella((short) 1).firmadaEn(Instant.now());
        signatureRepository.saveAndFlush(firma.build());

        assertThatThrownBy(() -> signatureRepository.saveAndFlush(firma.build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_firmas_vigente");
    }

    // ------------------------------------------------------------------
    // La corrección invalida la firma
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una pausa añadida y aprobada por el camino de siempre invalida la firma, con su porqué y su aviso")
    void correccionAprobada_invalidaLaFirma() {
        TimeEntry dia3 = jornada(ana, MARZO, 3);
        jornada(ana, MARZO, 4);
        MonthlySignatureResponse firma = signatureService.firmar(ana, MARZO, null);

        AddedPauseService.Resultado pedida = addedPauseService.anadir(dia3.getId(), new AddPauseRequest(
                dia3.getHoraEntrada().plus(4, ChronoUnit.HOURS),
                dia3.getHoraEntrada().plus(5, ChronoUnit.HOURS),
                "Olvidé fichar la comida"), ana);
        // Pedirla no cambia el registro: la firma sigue.
        assertThat(vigenteDe(ana, MARZO)).isNotNull();

        correctionService.resolver(pedida.correccion().id(), new ResolveCorrectionRequest(true, null), gestor);

        MonthlySignature caida = signatureRepository.findById(firma.id()).orElseThrow();
        assertThat(caida.getEstado()).isEqualTo(MonthlySignatureStatus.INVALIDADA);
        assertThat(caida.getInvalidadaEn()).isNotNull();
        assertThat(caida.getMotivoInvalidacion()).contains("03/03/2025");
        assertThat(vigenteDe(ana, MARZO)).isNull();
        assertThat(capturas.invalidadas).singleElement().satisfies(evento -> {
            assertThat(evento.anio()).isEqualTo(2025);
            assertThat(evento.mes()).isEqualTo(3);
            assertThat(evento.destinatarios()).extracting(User::getId).containsExactly(ana.getId());
        });

        // Y la comprobación dice que lo firmado ya no es lo que hay.
        assertThat(signatureService.verificar(firma.id(), ana).coincide()).isFalse();
    }

    @Test
    @DisplayName("Una corrección que deja las mismas horas no toca la firma: lo firmado sigue siendo verdad")
    void correccionSinCambios_noInvalida() {
        TimeEntry dia3 = jornada(ana, MARZO, 3);
        MonthlySignatureResponse firma = signatureService.firmar(ana, MARZO, null);

        // Anula el fichaje y crea otro igual: cambia el id, no lo firmado.
        CorrectionResponse pedida = correctionService.solicitar(dia3.getId(), new CorrectionRequestDTO(
                dia3.getHoraEntrada(), dia3.getHoraSalida(), "Revisión sin cambios"), ana);
        correctionService.resolver(pedida.id(), new ResolveCorrectionRequest(true, null), gestor);

        assertThat(signatureRepository.findById(firma.id()).orElseThrow().getEstado())
                .isEqualTo(MonthlySignatureStatus.VIGENTE);
        assertThat(capturas.invalidadas).isEmpty();
    }

    @Test
    @DisplayName("Mover un fichaje a otro mes invalida la firma del mes del que sale")
    void moverDeMes_invalidaElDeOrigen() {
        TimeEntry ultimoDeMarzo = jornada(ana, MARZO, 31);
        MonthlySignatureResponse firma = signatureService.firmar(ana, MARZO, null);

        Instant enAbril = ZonedDateTime.of(LocalDate.of(2025, 4, 1), LocalTime.of(9, 0), MADRID).toInstant();
        CorrectionResponse pedida = correctionService.solicitar(ultimoDeMarzo.getId(), new CorrectionRequestDTO(
                enAbril, enAbril.plus(8, ChronoUnit.HOURS), "Era el 1 de abril"), ana);
        correctionService.resolver(pedida.id(), new ResolveCorrectionRequest(true, null), gestor);

        assertThat(signatureRepository.findById(firma.id()).orElseThrow().getEstado())
                .isEqualTo(MonthlySignatureStatus.INVALIDADA);
    }

    @Test
    @DisplayName("Tras invalidarse se puede volver a firmar, y la caída se queda como histórico")
    void volverAFirmar() {
        TimeEntry dia3 = jornada(ana, MARZO, 3);
        MonthlySignatureResponse primera = signatureService.firmar(ana, MARZO, null);
        AddedPauseService.Resultado pedida = addedPauseService.anadir(dia3.getId(), new AddPauseRequest(
                dia3.getHoraEntrada().plus(4, ChronoUnit.HOURS), dia3.getHoraEntrada().plus(5, ChronoUnit.HOURS),
                "Comida"), ana);
        correctionService.resolver(pedida.correccion().id(), new ResolveCorrectionRequest(true, null), gestor);

        MonthlySignatureResponse segunda = signatureService.firmar(ana, MARZO, null);

        assertThat(segunda.hash()).isNotEqualTo(primera.hash());
        assertThat(segunda.segundosNetos()).isEqualTo(7 * 3600);
        assertThat(signatureRepository.findByUsuario_IdOrderByAnioDescMesDescFirmadaEnDesc(ana.getId()))
                .extracting(MonthlySignature::getEstado)
                .containsExactly(MonthlySignatureStatus.VIGENTE, MonthlySignatureStatus.INVALIDADA);
    }

    // ------------------------------------------------------------------
    // Visar, el equipo y el PDF
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RRHH visa la firma de otra persona, una sola vez, y nunca la suya")
    void visar() {
        jornada(ana, MARZO, 3);
        jornada(rrhh, MARZO, 3);
        MonthlySignatureResponse deAna = signatureService.firmar(ana, MARZO, null);
        MonthlySignatureResponse deElena = signatureService.firmar(rrhh, MARZO, null);

        assertThatThrownBy(() -> signatureService.visar(deAna.id(), gestor))
                .isInstanceOf(TenantAccessException.class);
        assertThatThrownBy(() -> signatureService.visar(deElena.id(), rrhh))
                .isInstanceOf(TenantAccessException.class)
                .hasMessageContaining("propia");

        MonthlySignatureResponse visada = signatureService.visar(deAna.id(), rrhh);
        assertThat(visada.visadaPor()).isEqualTo("Elena Prueba");
        assertThat(visada.visadaEn()).isNotNull();
        assertThatThrownBy(() -> signatureService.visar(deAna.id(), rrhh))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya está visada");
    }

    @Test
    @DisplayName("La vista del equipo dice quién ha firmado un mes y quién no")
    void equipo() {
        jornada(ana, MARZO, 3);
        signatureService.firmar(ana, MARZO, null);

        List<TeamSignatureResponse> equipo = signatureService.equipo(rrhh, MARZO);

        assertThat(equipo).extracting(TeamSignatureResponse::usuarioId, TeamSignatureResponse::estado)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(ana.getId(), "VIGENTE"),
                        org.assertj.core.groups.Tuple.tuple(gestor.getId(), "SIN_FIRMAR"),
                        org.assertj.core.groups.Tuple.tuple(rrhh.getId(), "SIN_FIRMAR"));
    }

    @Test
    @DisplayName("El informe del PDF lleva la firma vigente; sin firma, no lleva nada")
    void informeConFirma() {
        jornada(ana, MARZO, 3);
        assertThat(reportService.informeDeEmpleado(rrhh.getEmail(), ana.getId(), MARZO).firma()).isNull();

        MonthlySignatureResponse firma = signatureService.firmar(ana, MARZO, null);
        MonthlyReport.FirmaDelInforme enElInforme =
                reportService.informeDeEmpleado(rrhh.getEmail(), ana.getId(), MARZO).firma();

        assertThat(enElInforme).isNotNull();
        assertThat(enElInforme.hash()).isEqualTo(firma.hash());
        assertThat(enElInforme.firmadaPor()).isEqualTo("Ana");
    }

    // ------------------------------------------------------------------
    // Mis meses y el recordatorio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Mis meses: el pasado con jornadas se puede firmar; el firmado ya no; el vacío no sale")
    void misMeses() {
        YearMonth pasado = YearMonth.now(MADRID).minusMonths(1);
        YearMonth anterior = pasado.minusMonths(1);
        jornada(ana, pasado, 2);
        jornada(ana, anterior, 2);
        signatureService.firmar(ana, anterior, null);

        List<SignableMonthResponse> meses = signatureService.misMeses(ana);

        assertThat(meses).extracting(SignableMonthResponse::mes, SignableMonthResponse::puedeFirmar)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(pasado.getMonthValue(), true),
                        org.assertj.core.groups.Tuple.tuple(anterior.getMonthValue(), false));
        assertThat(meses.get(1).firma().estado()).isEqualTo(MonthlySignatureStatus.VIGENTE);
        // Las horas que se van a firmar, antes de firmarlas.
        assertThat(meses.get(0).segundosNetos()).isEqualTo(8 * 3600);
    }

    @Test
    @DisplayName("El recordatorio va a quien fichó y no ha firmado, y una sola vez al mes")
    void recordatorio() {
        LocalDate diaDos = LocalDate.of(2025, 4, 2);
        jornada(ana, MARZO, 3);
        jornada(gestor, MARZO, 3);
        signatureService.firmar(gestor, MARZO, null);

        signatureService.recordar(diaDos);

        // El recordatorio es global (todas las empresas): se mira lo de esta.
        assertThat(recordadosDeEstaEmpresa()).containsExactly(ana.getId());

        // Lo que deja el listener de avisos tras el commit, puesto a mano: así
        // el test no depende de cuánto tarda el hilo asíncrono.
        noticeRepository.save(Notice.builder().empresa(empresa).destinatario(ana)
                .tipo(NoticeType.RECORDATORIO_FIRMA).titulo("t").cuerpo("c").rutaDestino("firmas")
                .creadoEn(diaDos.atTime(9, 0).atZone(MADRID).toInstant()).build());
        capturas.recordatorios.clear();

        signatureService.recordar(diaDos.plusDays(1));

        assertThat(recordadosDeEstaEmpresa()).isEmpty();
    }

    private List<Long> recordadosDeEstaEmpresa() {
        return capturas.recordatorios.stream()
                .flatMap(evento -> evento.destinatarios().stream())
                .filter(persona -> persona.getEmpresa().getId() == empresa.getId())
                .map(User::getId)
                .toList();
    }

    @TestConfiguration
    static class CapturaDeAvisos {
        final List<NotificationEvents.SignatureInvalidated> invalidadas = new ArrayList<>();
        final List<NotificationEvents.SignatureReminder> recordatorios = new ArrayList<>();

        @EventListener
        void invalidada(NotificationEvents.SignatureInvalidated evento) {
            invalidadas.add(evento);
        }

        @EventListener
        void recordatorio(NotificationEvents.SignatureReminder evento) {
            recordatorios.add(evento);
        }
    }
}
