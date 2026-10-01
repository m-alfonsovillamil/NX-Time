package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.OvertimeAlert;
import com.nxtime.nxtime.domain.OvertimeType;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DailyHoursResponse;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.report.MonthlyReport;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.OvertimeAlertRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.DailyHoursService;
import com.nxtime.nxtime.service.MonthlySignatureService;
import com.nxtime.nxtime.service.OvertimeService;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La zona horaria es de cada empresa (ADR 032), contra PostgreSQL real.
 *
 * Dos empresas iguales, una en Madrid y otra en Canarias, fichan en el MISMO
 * instante: las 23:30 UTC. En invierno eso es la 00:30 del día siguiente en
 * Madrid y las 23:30 del mismo día en Canarias, así que el fichaje tiene que
 * caer en días distintos en cada una: en el gráfico de horas, en las horas
 * extra diarias, en el informe mensual y en el recordatorio de firma.
 *
 * Es el caso que antes salía mal en silencio: todo se contaba en Madrid, y
 * Canarias veía sus jornadas nocturnas en el día de después.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
@Import(ZonaHorariaIT.CapturaDeRecordatorios.class)
class ZonaHorariaIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "zona_horaria_it_" + System.nanoTime();
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

    private static final String CANARIAS = "Atlantic/Canary";

    /** Lunes 2 de marzo de 2026, 23:30 UTC: en Madrid ya es martes 3; en Canarias, todavía lunes 2. */
    private static final Instant LUNES_NOCHE = Instant.parse("2026-03-02T23:30:00Z");
    private static final LocalDate LUNES = LocalDate.of(2026, 3, 2);
    private static final LocalDate MARTES = LocalDate.of(2026, 3, 3);

    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TimeEntryRepository timeEntryRepository;
    @Autowired private OvertimeAlertRepository overtimeRepository;
    @Autowired private DailyHoursService dailyHoursService;
    @Autowired private OvertimeService overtimeService;
    @Autowired private ReportService reportService;
    @Autowired private MonthlySignatureService signatureService;
    @Autowired private CapturaDeRecordatorios capturas;

    private User enMadrid;
    private User enCanarias;

    @BeforeEach
    void setUp() {
        enMadrid = persona(empresa(Company.ZONA_POR_DEFECTO));
        enCanarias = persona(empresa(CANARIAS));
        capturas.recordatorios.clear();
    }

    @Test
    @DisplayName("Una empresa nueva queda en Madrid si no se dice otra zona")
    void zonaPorDefecto() {
        Company sinZona = companyRepository.save(Company.builder().nombre("Sin zona " + System.nanoTime()).build());

        assertThat(companyRepository.findById(sinZona.getId()).orElseThrow().zona())
                .isEqualTo(ZoneId.of("Europe/Madrid"));
    }

    @Test
    @DisplayName("El gráfico de horas pone la jornada en el día de cada empresa")
    void horasPorDia() {
        jornada(enMadrid, LUNES_NOCHE, 8 * 60);
        jornada(enCanarias, LUNES_NOCHE, 8 * 60);

        assertThat(minutosDelDia(enMadrid, LUNES)).isZero();
        assertThat(minutosDelDia(enMadrid, MARTES)).isEqualTo(8 * 60);
        assertThat(minutosDelDia(enCanarias, LUNES)).isEqualTo(8 * 60);
        assertThat(minutosDelDia(enCanarias, MARTES)).isZero();
    }

    @Test
    @DisplayName("Las horas extra diarias se avisan en el día de cada empresa, aunque el barrido sea uno solo")
    void horasExtraDiarias() {
        jornada(enMadrid, LUNES_NOCHE, 10 * 60);
        jornada(enCanarias, LUNES_NOCHE, 10 * 60);

        // Un barrido que solo pide el lunes: para Madrid la jornada es del
        // martes y no entra; para Canarias es del lunes y sí.
        overtimeService.detectar(LUNES, LUNES);

        assertThat(diasConExtraDiaria(enMadrid)).isEmpty();
        assertThat(diasConExtraDiaria(enCanarias)).containsExactly(LUNES);

        overtimeService.detectar(MARTES, MARTES);

        assertThat(diasConExtraDiaria(enMadrid)).containsExactly(MARTES);
        assertThat(diasConExtraDiaria(enCanarias)).containsExactly(LUNES);
    }

    @Test
    @DisplayName("El informe mensual corta el mes y pinta las horas en la zona de la empresa")
    void informeMensual() {
        // 28 de febrero, 23:30 UTC: en Madrid es 1 de marzo; en Canarias, febrero.
        Instant finDeFebrero = Instant.parse("2026-02-28T23:30:00Z");
        jornada(enMadrid, finDeFebrero, 8 * 60);
        jornada(enCanarias, finDeFebrero, 8 * 60);

        MonthlyReport marzoEnMadrid = reportService.informeDeEmpresa(enMadrid.getEmail(), YearMonth.of(2026, 3));
        MonthlyReport marzoEnCanarias = reportService.informeDeEmpresa(enCanarias.getEmail(), YearMonth.of(2026, 3));
        MonthlyReport febreroEnCanarias = reportService.informeDeEmpresa(enCanarias.getEmail(), YearMonth.of(2026, 2));

        assertThat(marzoEnMadrid.filas()).singleElement().satisfies(fila -> {
            assertThat(fila.fecha()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(fila.horaEntrada()).isEqualTo(LocalTime.of(0, 30));
        });
        assertThat(marzoEnCanarias.filas()).isEmpty();
        assertThat(febreroEnCanarias.filas()).singleElement().satisfies(fila -> {
            assertThat(fila.fecha()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(fila.horaEntrada()).isEqualTo(LocalTime.of(23, 30));
        });
        assertThat(febreroEnCanarias.zona()).isEqualTo(ZoneId.of(CANARIAS));
    }

    @Test
    @DisplayName("El recordatorio de firma cuenta 'el mes pasado' en la zona de cada empresa")
    void recordatorioDeFirma() {
        // La misma jornada: de febrero en Canarias, de marzo en Madrid.
        Instant finDeFebrero = Instant.parse("2026-02-28T23:30:00Z");
        jornada(enMadrid, finDeFebrero, 8 * 60);
        jornada(enCanarias, finDeFebrero, 8 * 60);

        // El 5 de marzo se recuerda firmar febrero.
        signatureService.recordar(LocalDate.of(2026, 3, 5));

        List<Long> recordados = capturas.recordatorios.stream()
                .flatMap(evento -> evento.destinatarios().stream())
                .map(User::getId)
                .toList();
        assertThat(recordados).contains(enCanarias.getId());
        assertThat(recordados).doesNotContain(enMadrid.getId());
    }

    // ------------------------------------------------------------------

    private Company empresa(String zona) {
        return companyRepository.save(Company.builder()
                .nombre("Empresa " + zona + " " + System.nanoTime())
                .zonaHoraria(zona)
                .build());
    }

    private User persona(Company empresa) {
        return userRepository.save(User.builder()
                .nombre("Persona").email("persona" + System.nanoTime() + "@test").contrasena("x")
                .rol(Role.RRHH).empresa(empresa).activo(true).horasSemanales(new BigDecimal("40.0"))
                .build());
    }

    private void jornada(User persona, Instant entrada, long minutos) {
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(persona).empresa(persona.getEmpresa())
                .horaEntrada(entrada).horaSalida(entrada.plus(minutos, ChronoUnit.MINUTES))
                .build());
    }

    private long minutosDelDia(User persona, LocalDate dia) {
        return dailyHoursService.horasPorDia(persona.getEmail(), dia, dia).stream()
                .mapToLong(DailyHoursResponse::minutosTrabajados)
                .sum();
    }

    private List<LocalDate> diasConExtraDiaria(User persona) {
        return overtimeRepository.findDeUsuarioEnRango(persona.getId(), LUNES.minusDays(7), MARTES.plusDays(7))
                .stream()
                .filter(aviso -> aviso.getTipo() == OvertimeType.DIARIA)
                .map(OvertimeAlert::getFecha)
                .toList();
    }

    @TestConfiguration
    static class CapturaDeRecordatorios {
        final List<NotificationEvents.SignatureReminder> recordatorios = new ArrayList<>();

        @EventListener
        void recordatorio(NotificationEvents.SignatureReminder evento) {
            recordatorios.add(evento);
        }
    }
}
