package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.CancelAbsenceRequest;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AbsenceService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cancelar una ausencia contra Postgres de verdad (ADR 058).
 *
 * <p>Lo que no ven los unitarios: que los CHECK de V54 dejan guardar una
 * CANCELADA venga de donde venga (de una pendiente, sin resolutor; de una
 * aprobada, conservándolo), y que una cancelada deja de contar para el saldo y
 * para los solapes, así que esos días se pueden volver a pedir.
 *
 * <p>Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class CancelarAusenciaIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "cancelar_aus_it_" + System.nanoTime();
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

    private Company empresa;
    private User empleada;
    private User gestora;
    /** Un lunes lejano: sin festivos nacionales cerca y dentro del mismo año. */
    private LocalDate lunes;

    @BeforeEach
    void preparar() {
        empresa = companyRepository.save(Company.builder().nombre("Cancelar " + System.nanoTime()).build());
        empleada = persona("Ana", Role.EMPLEADO);
        gestora = persona("Gema", Role.GESTOR);
        LocalDate hoy = LocalDate.now(empresa.zona());
        lunes = hoy.plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        if (lunes.getYear() != hoy.getYear() || lunes.getMonthValue() == 12) {
            lunes = hoy.withDayOfYear(1).plusYears(1).plusMonths(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        }
    }

    private User persona(String nombre, Role rol) {
        return userRepository.save(User.builder().nombre(nombre).apellidos("Pruebas")
                .email(nombre.toLowerCase() + "." + System.nanoTime() + "@nxtime.test")
                .contrasena("x").rol(rol).empresa(empresa).build());
    }

    private AbsenceResponse pedirVacaciones(LocalDate desde, LocalDate hasta) {
        return absenceService.createRequest(empleada.getEmail(),
                new AbsenceRequestDTO(desde, hasta, AbsenceType.VACACIONES, "Viaje"));
    }

    @Test
    @DisplayName("Una pendiente propia se retira; esos días se pueden volver a pedir y no gastan saldo")
    void retirarUnaPendiente() {
        AbsenceResponse pedida = pedirVacaciones(lunes, lunes.plusDays(4));
        assertThat(pedida.puedoCancelar()).isTrue();
        int disponiblesAntes = absenceService.getMyVacationBalance(empleada.getEmail(), lunes.getYear()).diasDisponibles();

        AbsenceResponse cancelada = absenceService.cancelar(empleada.getEmail(), pedida.id(), null);

        assertThat(cancelada.estado()).isEqualTo(AbsenceStatus.CANCELADA);
        assertThat(cancelada.puedoCancelar()).isFalse();
        AbsenceRequest guardada = absenceRequestRepository.findById(pedida.id()).orElseThrow();
        assertThat(guardada.getCanceladaPor().getId()).isEqualTo(empleada.getId());
        assertThat(guardada.getAprobadoPor()).isNull();
        assertThat(absenceService.getMyVacationBalance(empleada.getEmail(), lunes.getYear()).diasDisponibles())
                .isEqualTo(disponiblesAntes + 5);
        // Ya no se solapa con nada: los mismos días se pueden volver a pedir.
        assertThat(pedirVacaciones(lunes, lunes.plusDays(4)).estado()).isEqualTo(AbsenceStatus.PENDIENTE);
    }

    @Test
    @DisplayName("Una aprobada la anula quien aprueba, con motivo, y sigue constando quién la aprobó")
    void anularUnaAprobada() {
        AbsenceRequest aprobada = absenceRequestRepository.save(AbsenceRequest.builder()
                .usuario(empleada).empresa(empresa).tipo(AbsenceType.VACACIONES)
                .fechaInicio(LocalDate.now(empresa.zona()).minusDays(1))
                .fechaFin(LocalDate.now(empresa.zona()).plusDays(1))
                .estado(AbsenceStatus.APROBADA).aprobadoPor(gestora).fechaResolucion(Instant.now())
                .build());

        AbsenceResponse anulada = absenceService.cancelar(gestora.getEmail(), aprobada.getId(),
                new CancelAbsenceRequest("Se incorporó antes de lo previsto."));

        assertThat(anulada.estado()).isEqualTo(AbsenceStatus.CANCELADA);
        assertThat(anulada.aprobadoPor()).isNotNull();
        assertThat(anulada.canceladaPor()).isNotNull();
        assertThat(anulada.motivoCancelacion()).isEqualTo("Se incorporó antes de lo previsto.");
        AbsenceRequest guardada = absenceRequestRepository.findById(aprobada.getId()).orElseThrow();
        assertThat(guardada.getAprobadoPor().getId()).isEqualTo(gestora.getId());
        assertThat(guardada.getCanceladaPor().getId()).isEqualTo(gestora.getId());
        // Fuera del calendario del equipo.
        assertThat(absenceRequestRepository.findSolapadasDeEmpresa(empresa.getId(),
                aprobada.getFechaInicio(), aprobada.getFechaFin())).isEmpty();
    }
}
