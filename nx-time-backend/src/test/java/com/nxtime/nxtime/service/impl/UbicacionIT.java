package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.OrigenDelFichaje;
import com.nxtime.nxtime.domain.PuntoDeFichaje;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.UbicacionDelFichaje;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La ubicación al fichar contra PostgreSQL de verdad (ADR 056): solo con el
 * ajuste, redondeada, fuera de la auditoría, y borrada a los 12 meses y al
 * anonimizar a alguien.
 *
 * Requisito: `docker compose up -d postgres` (ver ApiContractTest).
 */
@SpringBootTest
class UbicacionIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "ubicacion_it_" + System.nanoTime();
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
    private ConservacionDeDatos conservacion;
    @Autowired
    private PersonalDataEraser borrado;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static final UbicacionDelFichaje EN_LA_OBRA =
            new UbicacionDelFichaje(PuntoDeFichaje.Estado.CAPTURADA, 43.3623456, -8.4115678, 25);

    private User persona(boolean pideUbicacion) {
        Company empresa = companyRepository.save(Company.builder().nombre("Ubicación " + System.nanoTime())
                .ubicacionAlFichar(pideUbicacion).build());
        return userRepository.save(User.builder()
                .email("tecnico" + System.nanoTime() + "@nxtime.test")
                .nombre("Técnico").contrasena("hash").rol(Role.EMPLEADO).empresa(empresa).activo(true).build());
    }

    @Test
    @DisplayName("Con el ajuste se guarda redondeada, sin permiso se ficha igual, y nada de eso entra en la auditoría")
    void seGuardaRedondeadaYFueraDeLaAuditoria() {
        User tecnico = persona(true);

        TimeEntry entrada = timeEntryService.registerTimeEntry(tecnico.getEmail(),
                new TimeEntryRequest(TimeEntryAction.INICIO, null, null, EN_LA_OBRA), OrigenDelFichaje.APP);
        timeEntryService.registerTimeEntry(tecnico.getEmail(), new TimeEntryRequest(TimeEntryAction.FIN, null, null,
                new UbicacionDelFichaje(PuntoDeFichaje.Estado.DENEGADA, null, null, null)), OrigenDelFichaje.APP);

        TimeEntry jornada = timeEntryRepository.findById(entrada.getId()).orElseThrow();
        assertThat(jornada.getUbicacionEntrada().getEstado()).isEqualTo(PuntoDeFichaje.Estado.CAPTURADA);
        assertThat(jornada.getUbicacionEntrada().getLatitud()).isEqualTo(43.3623);
        assertThat(jornada.getUbicacionEntrada().getLongitud()).isEqualTo(-8.4116);
        assertThat(jornada.getUbicacionEntrada().getPrecisionM()).isEqualTo(25);
        assertThat(jornada.getUbicacionSalida().getEstado()).isEqualTo(PuntoDeFichaje.Estado.DENEGADA);
        assertThat(jornada.getUbicacionSalida().getLatitud()).isNull();

        assertThat(jdbc.queryForList("SELECT coalesce(valor_nuevo::text, '') || coalesce(motivo, '') "
                + "FROM auditoria_fichaje WHERE registro_id = ?", String.class, jornada.getId()))
                .isNotEmpty()
                .noneMatch(fila -> fila.contains("43.36") || fila.contains("ubicacion") || fila.contains("CAPTURADA"));
    }

    @Test
    @DisplayName("Sin el ajuste no se guarda aunque la mande el cliente")
    void sinAjusteNoSeGuarda() {
        User tecnico = persona(false);

        TimeEntry entrada = timeEntryService.registerTimeEntry(tecnico.getEmail(),
                new TimeEntryRequest(TimeEntryAction.INICIO, null, null, EN_LA_OBRA), OrigenDelFichaje.APP);

        assertThat(timeEntryRepository.findById(entrada.getId()).orElseThrow().getUbicacionEntrada()).isNull();
        assertThat(timeEntryService.pideUbicacion(tecnico.getEmail())).isFalse();
    }

    @Test
    @DisplayName("A los 12 meses se borran las coordenadas y queda el estado; al anonimizar, también")
    void seBorraALosDoceMesesYAlAnonimizar() {
        User viejo = persona(true);
        Instant haceTreceMeses = Instant.now().minus(395, ChronoUnit.DAYS);
        TimeEntry antigua = timeEntryRepository.save(TimeEntry.builder()
                .usuario(viejo).empresa(viejo.getEmpresa())
                .horaEntrada(haceTreceMeses).horaSalida(haceTreceMeses.plus(8, ChronoUnit.HOURS))
                .ubicacionEntrada(PuntoDeFichaje.capturada(43.36, -8.41, 20)).build());
        User reciente = persona(true);
        Instant ayer = Instant.now().minus(1, ChronoUnit.DAYS);
        TimeEntry nueva = timeEntryRepository.save(TimeEntry.builder()
                .usuario(reciente).empresa(reciente.getEmpresa())
                .horaEntrada(ayer).horaSalida(ayer.plus(8, ChronoUnit.HOURS))
                .ubicacionEntrada(PuntoDeFichaje.capturada(43.36, -8.41, 20)).build());

        conservacion.aplicar(Instant.now());

        PuntoDeFichaje borrada = timeEntryRepository.findById(antigua.getId()).orElseThrow().getUbicacionEntrada();
        assertThat(borrada.getEstado()).isEqualTo(PuntoDeFichaje.Estado.CAPTURADA);
        assertThat(borrada.getLatitud()).isNull();
        assertThat(timeEntryRepository.findById(nueva.getId()).orElseThrow().getUbicacionEntrada().getLatitud())
                .isEqualTo(43.36);

        borrado.anonimizar(reciente.getId());
        assertThat(timeEntryRepository.findById(nueva.getId()).orElseThrow().getUbicacionEntrada().getLatitud())
                .isNull();
    }
}
