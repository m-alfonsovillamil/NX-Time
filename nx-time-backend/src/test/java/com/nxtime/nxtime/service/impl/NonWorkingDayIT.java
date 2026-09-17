package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.HolidayScope;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.HolidayCalendar;
import com.nxtime.nxtime.service.NonWorkingDayService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Qué día no es laborable para una persona, contra PostgreSQL real.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class NonWorkingDayIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "no_laborable_it_" + System.nanoTime();
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
    private NonWorkingDayService service;
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
    private Company otraEmpresa;
    private User ana;
    private User javi;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        otraEmpresa = companyRepository.save(Company.builder().nombre("Otra " + System.nanoTime()).build());
        ana = persona("ana", empresa);
        javi = persona("javi", empresa);
    }

    private User persona(String nombre, Company de) {
        return userRepository.save(User.builder()
                .nombre(nombre).email(nombre + System.nanoTime() + "@test").contrasena("x")
                .rol(Role.EMPLEADO).empresa(de).activo(true).horasSemanales(new BigDecimal("40.0"))
                .build());
    }

    private void festivo(Company de, LocalDate dia, String nombre) {
        holidayRepository.save(Holiday.builder()
                .empresa(de).fecha(dia).descripcion(nombre)
                .ambito(de == null ? HolidayScope.NACIONAL : HolidayScope.EMPRESA).build());
        holidayCalendar.invalidar();
    }

    private void ausencia(User de, LocalDate inicio, LocalDate fin, AbsenceType tipo, AbsenceStatus estado) {
        boolean pendiente = estado == AbsenceStatus.PENDIENTE;
        absenceRepository.save(AbsenceRequest.builder()
                .usuario(de).empresa(de.getEmpresa()).fechaInicio(inicio).fechaFin(fin).tipo(tipo).estado(estado)
                .aprobadoPor(pendiente ? null : de).fechaResolucion(pendiente ? null : Instant.now())
                .build());
    }

    @Test
    @DisplayName("Festivo nacional y festivo de la empresa, con su nombre; el de otra empresa no")
    void festivos() {
        LocalDate nacional = LocalDate.of(2031, 10, 12);
        LocalDate deEmpresa = LocalDate.of(2031, 3, 5);
        LocalDate deOtra = LocalDate.of(2031, 3, 6);
        festivo(null, nacional, "Fiesta Nacional de España");
        festivo(empresa, deEmpresa, "Fiesta de la empresa");
        festivo(otraEmpresa, deOtra, "Fiesta ajena");

        assertThat(service.motivo(ana, nacional)).get()
                .satisfies(m -> assertThat(m.texto()).isEqualTo("Festivo: Fiesta Nacional de España"));
        assertThat(service.motivo(ana, deEmpresa)).get()
                .satisfies(m -> assertThat(m.vacaciones()).isFalse());
        assertThat(service.motivo(ana, deOtra)).isEmpty();
    }

    @Test
    @DisplayName("Ausencia aprobada sí (y dice si eran vacaciones); pendiente no; la de otra persona no")
    void ausencias() {
        LocalDate lunes = LocalDate.of(2031, 6, 2);
        ausencia(ana, lunes, lunes.plusDays(4), AbsenceType.VACACIONES, AbsenceStatus.APROBADA);
        ausencia(ana, lunes.plusDays(7), lunes.plusDays(7), AbsenceType.MEDICO, AbsenceStatus.APROBADA);
        ausencia(ana, lunes.plusDays(8), lunes.plusDays(8), AbsenceType.VACACIONES, AbsenceStatus.PENDIENTE);

        assertThat(service.motivo(ana, lunes.plusDays(2))).get()
                .satisfies(m -> {
                    assertThat(m.texto()).isEqualTo("Vacaciones");
                    assertThat(m.vacaciones()).isTrue();
                });
        assertThat(service.motivo(ana, lunes.plusDays(7))).get()
                .satisfies(m -> assertThat(m.vacaciones()).isFalse());
        assertThat(service.motivo(ana, lunes.plusDays(8))).isEmpty();
        assertThat(service.motivo(javi, lunes.plusDays(2))).isEmpty();
    }

    /* Hay empresas que trabajan sábados: el fin de semana no avisa. */
    @Test
    @DisplayName("Un sábado sin festivo ni ausencia es laborable a efectos del aviso")
    void finDeSemanaNoCuenta() {
        assertThat(service.motivo(ana, LocalDate.of(2031, 6, 7))).isEmpty();
    }
}
