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
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DailyHoursResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.DailyHoursService;
import com.nxtime.nxtime.service.HolidayCalendar;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
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
 * Las horas día a día de la pantalla de inicio, contra PostgreSQL real.
 *
 * La semana del 2 al 8 de marzo de 2026 (lunes a domingo), con un festivo de
 * empresa el miércoles, unas vacaciones aprobadas el jueves y una pendiente el
 * viernes: tiene que decir cuánto se trabajó cada día y cuánto se esperaba, y
 * por qué no se esperaba nada los días que no.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class DailyHoursIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "horas_dia_it_" + System.nanoTime();
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
    private DailyHoursService service;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private AbsenceRequestRepository absenceRepository;
    @Autowired
    private HolidayCalendar holidayCalendar;

    private Company empresa;
    private User ana;

    private static final LocalDate LUNES = LocalDate.of(2026, 3, 2);

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ana = userRepository.save(User.builder()
                .nombre("Ana").email("ana" + System.nanoTime() + "@test").contrasena("x")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).horasSemanales(new BigDecimal("40.0"))
                .build());
    }

    private void fichaje(String entradaUtc, long minutos, long segundosPausa, boolean anulado) {
        Instant entrada = Instant.parse(entradaUtc);
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(ana).empresa(empresa)
                .horaEntrada(entrada).horaSalida(entrada.plus(minutos, ChronoUnit.MINUTES))
                .segundosPausaAcumulados(segundosPausa).anulado(anulado)
                .build());
    }

    private void ausencia(LocalDate dia, AbsenceStatus estado) {
        absenceRepository.save(AbsenceRequest.builder()
                .usuario(ana).empresa(empresa).fechaInicio(dia).fechaFin(dia)
                .tipo(AbsenceType.VACACIONES).estado(estado)
                .aprobadoPor(estado == AbsenceStatus.PENDIENTE ? null : ana)
                .fechaResolucion(estado == AbsenceStatus.PENDIENTE ? null : Instant.now())
                .build());
    }

    @Test
    @DisplayName("Trabajado y esperado por día, con el festivo y la ausencia con nombre")
    void unaSemanaCompleta() {
        holidayRepository.save(Holiday.builder()
                .empresa(empresa).fecha(LUNES.plusDays(2)).descripcion("Fiesta de la empresa")
                .ambito(HolidayScope.EMPRESA).build());
        holidayCalendar.invalidar();
        ausencia(LUNES.plusDays(3), AbsenceStatus.APROBADA);
        ausencia(LUNES.plusDays(4), AbsenceStatus.PENDIENTE);

        // Lunes: 8 h con 30 min de pausa. Dos fichajes el mismo día se suman.
        fichaje("2026-03-02T07:00:00Z", 240, 0, false);
        fichaje("2026-03-02T12:00:00Z", 240, 1800, false);
        // Martes a las 23:30 UTC = miércoles 00:30 en Madrid: cuenta el miércoles.
        fichaje("2026-03-03T23:30:00Z", 60, 0, false);
        // Un anulado no cuenta.
        fichaje("2026-03-06T07:00:00Z", 480, 0, true);

        List<DailyHoursResponse> dias = service.horasPorDia(ana.getEmail(), LUNES, LUNES.plusDays(6));

        assertThat(dias).extracting(DailyHoursResponse::fecha)
                .containsExactly(LUNES, LUNES.plusDays(1), LUNES.plusDays(2), LUNES.plusDays(3),
                        LUNES.plusDays(4), LUNES.plusDays(5), LUNES.plusDays(6));
        assertThat(dias).extracting(DailyHoursResponse::minutosTrabajados)
                .containsExactly(450L, 0L, 60L, 0L, 0L, 0L, 0L);
        // 40 h / 5 = 480 min los días laborables; 0 en festivo, ausencia
        // aprobada y fin de semana. La pendiente no cambia nada.
        assertThat(dias).extracting(DailyHoursResponse::minutosEsperados)
                .containsExactly(480L, 480L, 0L, 0L, 480L, 0L, 0L);
        assertThat(dias.get(2).festivo()).isEqualTo("Fiesta de la empresa");
        assertThat(dias.get(3).ausencia()).isEqualTo("Vacaciones");
        assertThat(dias.get(4).ausencia()).isNull();
    }

    @Test
    @DisplayName("Al revés o de más de 62 días: 400")
    void rangosInvalidos() {
        assertThatThrownBy(() -> service.horasPorDia(ana.getEmail(), LUNES.plusDays(1), LUNES))
                .isInstanceOf(BusinessException.class);
        service.horasPorDia(ana.getEmail(), LUNES, LUNES.plusDays(61));
        assertThatThrownBy(() -> service.horasPorDia(ana.getEmail(), LUNES, LUNES.plusDays(62)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("62");
    }
}
