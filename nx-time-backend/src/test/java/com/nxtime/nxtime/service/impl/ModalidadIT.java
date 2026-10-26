package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Modalidad;
import com.nxtime.nxtime.domain.OrigenDelFichaje;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.ResolveCorrectionRequest;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.dto.UpdateCompanySettingsRequest;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.CompanySettingsService;
import com.nxtime.nxtime.service.CorrectionService;
import com.nxtime.nxtime.service.ReportService;
import com.nxtime.nxtime.service.TimeEntryService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Presencial o a distancia contra PostgreSQL de verdad (ADR 055): el ajuste de
 * la empresa, la jornada, la corrección, el informe y la auditoría.
 *
 * Requisito: `docker compose up -d postgres` (ver ApiContractTest).
 */
@SpringBootTest
class ModalidadIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "modalidad_it_" + System.nanoTime();
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
    private TimeEntryService timeEntryService;
    @Autowired
    private CorrectionService correctionService;
    @Autowired
    private CompanySettingsService ajustes;
    @Autowired
    private ReportService informes;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private User persona(Company empresa, String nombre, Role rol) {
        return userRepository.save(User.builder()
                .email(nombre.toLowerCase() + System.nanoTime() + "@nxtime.test")
                .nombre(nombre).contrasena("hash").rol(rol).empresa(empresa).activo(true).build());
    }

    private Company empresa() {
        return companyRepository.save(Company.builder().nombre("Modalidad " + System.nanoTime()).build());
    }

    private static TimeEntryRequest entrada(Modalidad modalidad) {
        return new TimeEntryRequest(TimeEntryAction.INICIO, null, modalidad);
    }

    @Test
    @DisplayName("Sin el ajuste no se guarda aunque la mande el cliente; con él, sí, y la lleva la auditoría")
    void soloSiLaEmpresaLaPregunta() {
        Company empresa = empresa();
        User admin = persona(empresa, "Admin", Role.ADMIN);
        User ana = persona(empresa, "Ana", Role.EMPLEADO);

        TimeEntry sinAjuste = timeEntryService.registerTimeEntry(ana.getEmail(), entrada(Modalidad.A_DISTANCIA),
                OrigenDelFichaje.WEB);
        assertThat(sinAjuste.getModalidad()).isNull();
        assertThat(timeEntryService.preguntaModalidad(ana.getEmail())).isFalse();
        timeEntryService.registerTimeEntry(ana.getEmail(), new TimeEntryRequest(TimeEntryAction.FIN), null);

        var guardado = ajustes.guardar(new UpdateCompanySettingsRequest(empresa.getNombre(), "Europe/Madrid", null,
                null, true), admin);
        assertThat(guardado.preguntarModalidad()).isTrue();
        assertThat(timeEntryService.preguntaModalidad(ana.getEmail())).isTrue();

        TimeEntry conAjuste = timeEntryService.registerTimeEntry(ana.getEmail(), entrada(Modalidad.A_DISTANCIA),
                OrigenDelFichaje.WEB);
        assertThat(timeEntryRepository.findById(conAjuste.getId()).orElseThrow().getModalidad())
                .isEqualTo(Modalidad.A_DISTANCIA);
        assertThat(jdbc.queryForObject("SELECT valor_nuevo::text FROM auditoria_fichaje WHERE registro_id = ? "
                + "AND accion = 'CREACION'", String.class, conAjuste.getId())).contains("A_DISTANCIA");

        // Guardar los ajustes sin decir nada de la modalidad no la apaga.
        ajustes.guardar(new UpdateCompanySettingsRequest(empresa.getNombre(), "Europe/Madrid", null), admin);
        assertThat(timeEntryService.preguntaModalidad(ana.getEmail())).isTrue();
    }

    @Test
    @DisplayName("Si la pregunta y no llega, se ficha igual, sin modalidad")
    void sinModalidadSeFichaIgual() {
        Company empresa = companyRepository.save(Company.builder().nombre("Modalidad sin " + System.nanoTime())
                .preguntarModalidad(true).build());
        User luis = persona(empresa, "Luis", Role.EMPLEADO);

        TimeEntry jornada = timeEntryService.registerTimeEntry(luis.getEmail(),
                new TimeEntryRequest(TimeEntryAction.INICIO), OrigenDelFichaje.APP);

        assertThat(jornada.getId()).isPositive();
        assertThat(jornada.getModalidad()).isNull();
    }

    @Test
    @DisplayName("Se corrige como las horas, y sale en el informe de horas")
    void seCorrigeYSaleEnElInforme() {
        Company empresa = companyRepository.save(Company.builder().nombre("Modalidad corr " + System.nanoTime())
                .preguntarModalidad(true).build());
        User rrhh = persona(empresa, "Rrhh", Role.RRHH);
        User marta = persona(empresa, "Marta", Role.EMPLEADO);
        Instant hace = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        TimeEntry jornada = timeEntryRepository.save(TimeEntry.builder()
                .usuario(marta).empresa(empresa).horaEntrada(hace).horaSalida(hace.plus(8, ChronoUnit.HOURS))
                .modalidad(Modalidad.PRESENCIAL).origen(OrigenDelFichaje.APP).build());

        var pedida = correctionService.solicitar(jornada.getId(), new CorrectionRequestDTO(
                jornada.getHoraEntrada(), jornada.getHoraSalida(), "Ese día teletrabajé", null, null, null,
                Modalidad.A_DISTANCIA), marta);
        assertThat(pedida.modalidadActual()).isEqualTo(Modalidad.PRESENCIAL);
        assertThat(pedida.modalidadPropuesta()).isEqualTo(Modalidad.A_DISTANCIA);
        correctionService.resolver(pedida.id(), new ResolveCorrectionRequest(true, null), rrhh);

        TimeEntry corregida = timeEntryRepository.findByRegistroOriginal_Id(jornada.getId()).orElseThrow();
        assertThat(corregida.getModalidad()).isEqualTo(Modalidad.A_DISTANCIA);
        assertThat(corregida.getOrigen()).isEqualTo(OrigenDelFichaje.APP);

        YearMonth mes = YearMonth.from(hace.atZone(ZoneId.of("Europe/Madrid")));
        var informe = informes.informeDeEmpresa(rrhh.getEmail(), mes);
        assertThat(informe.filas()).anySatisfy(fila -> assertThat(fila.modalidad()).isEqualTo(Modalidad.A_DISTANCIA));
    }
}
