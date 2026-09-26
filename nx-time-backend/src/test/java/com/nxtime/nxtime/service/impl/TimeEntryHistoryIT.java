package com.nxtime.nxtime.service.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El historial filtrado por periodo, contra PostgreSQL real.
 *
 * Lo delicado son los bordes: los días son de España y las marcas se guardan
 * en UTC. Un fichaje a las 23:30 UTC del 30 de septiembre es del 1 de octubre
 * en Madrid, y tiene que salir en octubre y no en septiembre.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class TimeEntryHistoryIT {

    private static final Pageable PAGINA = PageRequest.of(0, 200);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "historial_it_" + System.nanoTime();
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
    private TimeEntryService service;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    private Company empresa;
    private User ana;
    private User javi;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ana = persona("ana");
        javi = persona("javi");
    }

    private User persona(String nombre) {
        return userRepository.save(User.builder()
                .nombre(nombre).email(nombre + System.nanoTime() + "@test").contrasena("x")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).horasSemanales(new BigDecimal("40.0"))
                .build());
    }

    private TimeEntry fichaje(User quien, String entradaUtc, boolean anulado) {
        Instant entrada = Instant.parse(entradaUtc);
        return timeEntryRepository.save(TimeEntry.builder()
                .usuario(quien).empresa(empresa)
                .horaEntrada(entrada).horaSalida(entrada.plus(1, ChronoUnit.HOURS))
                .anulado(anulado)
                .build());
    }

    @Test
    @DisplayName("Los días son de España: el borde de medianoche cae en el día que toca")
    void bordesEnHoraDeEspana() {
        // 1/9 a las 00:30 en Madrid (22:30 UTC del 31/8): es de septiembre.
        TimeEntry primeroDeSeptiembre = fichaje(ana, "2026-08-31T22:30:00Z", false);
        // 30/9 a las 23:30 en Madrid (21:30 UTC): es de septiembre.
        TimeEntry ultimoDeSeptiembre = fichaje(ana, "2026-09-30T21:30:00Z", false);
        // 30/9 a las 23:30 UTC = 1/10 a la 01:30 en Madrid: es de octubre.
        TimeEntry primeroDeOctubre = fichaje(ana, "2026-09-30T23:30:00Z", false);
        // 31/8 a las 23:30 en Madrid (21:30 UTC): es de agosto.
        TimeEntry deAgosto = fichaje(ana, "2026-08-31T21:30:00Z", false);

        assertThat(service.getHistory(ana.getEmail(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), PAGINA))
                .extracting(TimeEntry::getId)
                .containsExactly(ultimoDeSeptiembre.getId(), primeroDeSeptiembre.getId());
        assertThat(service.getHistory(ana.getEmail(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), PAGINA))
                .extracting(TimeEntry::getId).containsExactly(primeroDeOctubre.getId());
        assertThat(service.getHistory(ana.getEmail(), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 31), PAGINA))
                .extracting(TimeEntry::getId).containsExactly(deAgosto.getId());
    }

    @Test
    @DisplayName("Sin los anulados y sin los de otra persona")
    void sinAnuladosNiAjenos() {
        TimeEntry bueno = fichaje(ana, "2026-09-10T07:00:00Z", false);
        fichaje(ana, "2026-09-11T07:00:00Z", true);
        fichaje(javi, "2026-09-12T07:00:00Z", false);

        assertThat(service.getHistory(ana.getEmail(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), PAGINA))
                .extracting(TimeEntry::getId).containsExactly(bueno.getId());
    }

    /*
     * Desde la Fase A7 el periodo también va por páginas: un año entero con
     * más de 200 fichajes se lee en dos, sin perder ni repetir ninguno. Es
     * lo que hace la app para sumar las horas de un periodo.
     */
    @Test
    @DisplayName("Un año con más de 200 fichajes se lee entero en dos páginas")
    void unAnoEnDosPaginas() {
        for (int i = 0; i < 205; i++) {
            fichaje(ana, Instant.parse("2025-01-01T08:00:00Z").plus(i, ChronoUnit.DAYS).toString(), false);
        }
        LocalDate desde = LocalDate.of(2025, 1, 1);
        LocalDate hasta = LocalDate.of(2025, 12, 31);

        Page<TimeEntry> primera = service.getHistory(ana.getEmail(), desde, hasta, PageRequest.of(0, 200));
        Page<TimeEntry> segunda = service.getHistory(ana.getEmail(), desde, hasta, PageRequest.of(1, 200));

        assertThat(primera.getTotalElements()).isEqualTo(205);
        assertThat(primera.hasNext()).isTrue();
        assertThat(segunda.getContent()).hasSize(5);
        assertThat(segunda.hasNext()).isFalse();
        java.util.Set<Long> ids = new java.util.HashSet<>();
        primera.forEach(f -> ids.add(f.getId()));
        segunda.forEach(f -> ids.add(f.getId()));
        assertThat(ids).hasSize(205);
    }

    @Test
    @DisplayName("Periodo al revés o de más de un año: 400")
    void periodosInvalidos() {
        assertThatThrownBy(() -> service.getHistory(ana.getEmail(), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), PAGINA))
                .isInstanceOf(BusinessException.class).hasMessageContaining("posterior");
        // 366 días sí (un bisiesto entero); 367 no.
        service.getHistory(ana.getEmail(), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), PAGINA);
        assertThatThrownBy(() -> service.getHistory(ana.getEmail(), LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), PAGINA))
                .isInstanceOf(BusinessException.class).hasMessageContaining("un año");
    }
}
