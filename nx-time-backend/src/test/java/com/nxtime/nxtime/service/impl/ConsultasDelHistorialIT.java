package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Kiosk;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.KioskRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cuántas consultas cuesta el historial del equipo (revisión del 1/10/2026,
 * ADR 034, punto 12).
 *
 * <p>Las relaciones de JPA van {@code EAGER} por defecto, y las nuevas del
 * kiosco lo arrastraban todo: cada fichaje hecho en un kiosco cargaba el
 * kiosco, el kiosco a quien lo dio de alta, y esa persona su empresa y su
 * departamento; y cada corrección, la cadena entera de fichajes originales.
 * Este test fija el número de consultas para que no vuelva a crecer sin que
 * se note. Medido al escribirlo (20 jornadas, la mitad de kiosco y una cadena
 * de correcciones): 9 consultas con todo EAGER, 5 con esas tres
 * relaciones LAZY.
 *
 * <p>Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ConsultasDelHistorialIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "consultas_it_" + System.nanoTime();
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

    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private KioskRepository kioskRepository;
    @Autowired private TimeEntryRepository timeEntryRepository;
    @Autowired private TimeEntryService timeEntryService;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private static final int JORNADAS = 20;

    @Test
    @DisplayName("Una página del historial del equipo con fichajes de kiosco y correcciones cuesta pocas consultas")
    void elHistorialNoArrastraLoQueNoEnsena() {
        Company empresa = companyRepository.save(Company.builder().nombre("Consultas " + System.nanoTime()).build());
        User admin = userRepository.save(User.builder().nombre("Raúl").email("raul." + System.nanoTime() + "@nxtime.test")
                .contrasena("x").rol(Role.ADMIN).empresa(empresa).build());
        List<User> plantilla = List.of(persona(empresa, "Ana"), persona(empresa, "Luis"), persona(empresa, "Eva"));
        Kiosk kiosco = kioskRepository.save(Kiosk.builder().empresa(empresa).nombre("Entrada").creadoPor(admin).build());

        Instant dia = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(40, ChronoUnit.DAYS);
        TimeEntry anterior = null;
        for (int i = 0; i < JORNADAS; i++) {
            Instant entrada = dia.plus(i, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS);
            anterior = timeEntryRepository.save(TimeEntry.builder()
                    .usuario(plantilla.get(i % plantilla.size())).empresa(empresa)
                    .horaEntrada(entrada).horaSalida(entrada.plus(8, ChronoUnit.HOURS))
                    .kiosco(i % 2 == 0 ? kiosco : null)
                    // Una cadena de correcciones: cada uno corrige al anterior.
                    .registroOriginal(i % 4 == 3 ? anterior : null)
                    .build());
        }

        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        List<TeamTimeEntryDTO> pagina = timeEntryService
                .getTeamHistory(admin.getEmail(), null, PageRequest.of(0, JORNADAS)).getContent();

        long consultas = estadisticas.getPrepareStatementCount();
        assertThat(pagina).hasSize(JORNADAS);
        assertThat(pagina.stream().filter(fila -> "Entrada".equals(fila.kiosco()))).hasSize(JORNADAS / 2);
        assertThat(consultas).as("consultas para una página de %d jornadas", JORNADAS).isLessThanOrEqualTo(6);
    }

    private User persona(Company empresa, String nombre) {
        return userRepository.save(User.builder().nombre(nombre).email(nombre.toLowerCase() + "." + System.nanoTime()
                + "@nxtime.test").contrasena("x").rol(Role.EMPLEADO).empresa(empresa).build());
    }
}
