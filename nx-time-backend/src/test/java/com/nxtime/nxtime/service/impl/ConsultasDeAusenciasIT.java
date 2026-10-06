package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AbsenceService;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cuántas consultas cuestan las ausencias del equipo (plan del 6/10/2026).
 *
 * <p>Se midieron con los datos de demo los cincuenta listados de la API. Casi
 * todos cuestan un número fijo de consultas, crezcan lo que crezcan. Estos dos
 * no: la bandeja de pendientes y el historial de resueltas traían a la persona
 * de cada ausencia --y a quien la resolvió-- con <b>una consulta por
 * persona</b>. Con las cuatro de la demo no se notaba; con cuarenta peticiones
 * de cuarenta personas eran cuarenta consultas más.
 *
 * <p>Medido al escribirlo: 18 consultas con doce personas y 29 con
 * veinticuatro antes; 4 y 3 después (la primera llamada llena una caché).
 *
 * <p>El test no fija un número: comprueba que <b>no crece al doblar la
 * gente</b>, que es lo que distingue un listado sano de uno que empeora con el
 * tamaño de la empresa.
 *
 * <p>Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ConsultasDeAusenciasIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "consultas_aus_it_" + System.nanoTime();
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
    @Autowired private AbsenceRequestRepository absenceRequestRepository;
    @Autowired private AbsenceService absenceService;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Company empresa;
    private User rrhh;
    private final List<User> aprobadores = new ArrayList<>();

    @BeforeEach
    void preparar() {
        empresa = companyRepository.save(Company.builder().nombre("Ausencias " + System.nanoTime()).build());
        rrhh = persona("Rita", Role.RRHH);
        aprobadores.clear();
        aprobadores.add(persona("Gema", Role.GESTOR));
        aprobadores.add(persona("Gil", Role.GESTOR));
    }

    private User persona(String nombre, Role rol) {
        return userRepository.save(User.builder().nombre(nombre).apellidos("Pruebas")
                .email(nombre.toLowerCase() + "." + System.nanoTime() + "@nxtime.test")
                .contrasena("x").rol(rol).empresa(empresa).build());
    }

    /** Añade {@code cuantas} personas, cada una con una ausencia pendiente y otra ya aprobada. */
    private void gente(int cuantas) {
        for (int i = 0; i < cuantas; i++) {
            User empleado = persona("Empleado" + i, Role.EMPLEADO);
            LocalDate dia = LocalDate.of(2026, 11, 2).plusDays(i);
            absenceRequestRepository.save(AbsenceRequest.builder()
                    .usuario(empleado).empresa(empresa).tipo(AbsenceType.ASUNTOS_PROPIOS)
                    .fechaInicio(dia).fechaFin(dia).build());
            absenceRequestRepository.save(AbsenceRequest.builder()
                    .usuario(empleado).empresa(empresa).tipo(AbsenceType.ASUNTOS_PROPIOS)
                    .fechaInicio(dia.minusMonths(1)).fechaFin(dia.minusMonths(1))
                    .estado(AbsenceStatus.APROBADA)
                    .aprobadoPor(aprobadores.get(i % aprobadores.size())).fechaResolucion(Instant.now())
                    .build());
        }
    }

    private long consultasDe(Supplier<Integer> listado, int filasEsperadas) {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();
        int filas = listado.get();
        long consultas = estadisticas.getPrepareStatementCount();
        assertThat(filas).isEqualTo(filasEsperadas);
        return consultas;
    }

    @Test
    @DisplayName("La bandeja de ausencias pendientes cuesta lo mismo con doce personas que con veinticuatro")
    void lasPendientesNoCrecenConLaGente() {
        gente(12);
        long conDoce = consultasDe(() -> absenceService.getPendingRequests(rrhh.getEmail()).size(), 12);
        gente(12);
        long conVeinticuatro = consultasDe(() -> absenceService.getPendingRequests(rrhh.getEmail()).size(), 24);

        assertThat(conVeinticuatro)
                .as("consultas con 24 personas (con 12 fueron %d)", conDoce)
                .isLessThanOrEqualTo(conDoce);
    }

    @Test
    @DisplayName("Una página del historial de ausencias resueltas cuesta lo mismo con doce personas que con veinticuatro")
    void elHistorialNoCreceConLaGente() {
        gente(12);
        long conDoce = consultasDe(
                () -> absenceService.getHistory(rrhh.getEmail(), PageRequest.of(0, 50)).contenido().size(), 12);
        gente(12);
        long conVeinticuatro = consultasDe(
                () -> absenceService.getHistory(rrhh.getEmail(), PageRequest.of(0, 50)).contenido().size(), 24);

        assertThat(conVeinticuatro)
                .as("consultas con 24 personas (con 12 fueron %d)", conDoce)
                .isLessThanOrEqualTo(conDoce);
    }
}
