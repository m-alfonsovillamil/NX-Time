package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.report.MonthlyReport;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.ReportService;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cuántas consultas cuestan los registros mensuales de toda la plantilla, los
 * del ZIP para la Inspección (revisión del 28/11/2026).
 *
 * <p>Eran tres consultas por persona y mes (sus fichajes, su firma y sus
 * fichajes sin conexión): con 87 personas, un ZIP de tres meses hacía 806.
 * Este test fija que no crezcan con la plantilla, y que por lotes salga
 * exactamente lo mismo que el registro de cada persona pedido uno a uno: con
 * firma vigente, con firma invalidada (que no cuenta) y con un fichaje hecho
 * sin conexión.
 *
 * <p>Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ConsultasDeLaInspeccionIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "inspeccion_consultas_it_" + System.nanoTime();
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

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final YearMonth MES = YearMonth.of(2026, 3);

    @Autowired private ReportService informes;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Company empresa;
    private User rrhh;
    private final List<User> plantilla = new ArrayList<>();

    @BeforeEach
    void preparar() {
        empresa = companyRepository.save(Company.builder().nombre("Inspección " + System.nanoTime()).build());
        rrhh = persona("Rita", Role.RRHH);
    }

    @Test
    @DisplayName("Los registros de toda la plantilla cuestan las mismas consultas con 3 personas que con 9")
    void lasConsultasNoCrecenConLaPlantilla() {
        conJornadas(3);
        long conTres = consultasDe(() -> informes.informesDeLaPlantilla(rrhh.getEmail(), MES));

        conJornadas(6);
        long conNueve = consultasDe(() -> informes.informesDeLaPlantilla(rrhh.getEmail(), MES));

        assertThat(informes.informesDeLaPlantilla(rrhh.getEmail(), MES)).hasSize(9);
        assertThat(conNueve).as("consultas con 9 personas frente a %d con 3", conTres).isLessThanOrEqualTo(conTres);
    }

    @Test
    @DisplayName("Por lotes sale lo mismo que el registro de cada persona: firma vigente, invalidada y sin conexión")
    void porLotesSaleLoMismoQueUnoAUno() {
        conJornadas(3);
        User conFirma = plantilla.get(0);
        User conFirmaInvalidada = plantilla.get(1);
        User sinConexion = plantilla.get(2);
        firma(conFirma, "VIGENTE");
        firma(conFirmaInvalidada, "INVALIDADA");
        jdbc.update("UPDATE registros SET origen = 'APP_SIN_CONEXION' WHERE id = "
                + "(SELECT min(id) FROM registros WHERE usuario_id = ?)", sinConexion.getId());

        List<MonthlyReport> porLotes = informes.informesDeLaPlantilla(rrhh.getEmail(), MES);
        List<MonthlyReport> unoAUno = plantilla.stream()
                .map(persona -> informes.informeDeEmpleado(rrhh.getEmail(), persona.getId(), MES))
                .toList();

        assertThat(porLotes).containsExactlyInAnyOrderElementsOf(unoAUno);
        // Y que los casos de verdad se distinguen (si no, comparar no prueba nada).
        assertThat(porLotes).filteredOn(informe -> informe.firma() != null).hasSize(1);
    }

    // ------------------------------------------------------------------

    private void conJornadas(int cuantas) {
        for (int i = 0; i < cuantas; i++) {
            User persona = persona("Persona" + plantilla.size(), Role.EMPLEADO);
            jornada(persona, 3);
            jornada(persona, 4);
            plantilla.add(persona);
        }
    }

    private void jornada(User quien, int dia) {
        Timestamp entrada = Timestamp.from(MES.atDay(dia).atTime(8, 0).atZone(MADRID).toInstant());
        Timestamp salida = Timestamp.from(MES.atDay(dia).atTime(16, 0).atZone(MADRID).toInstant());
        jdbc.update("INSERT INTO registros (usuario_id, empresa_id, hora_entrada, hora_salida, anulado) VALUES (?, ?, ?, ?, false)",
                quien.getId(), empresa.getId(), entrada, salida);
    }

    private void firma(User quien, String estado) {
        Timestamp firmada = Timestamp.from(MES.plusMonths(1).atDay(1).atStartOfDay(MADRID).toInstant());
        // Una invalidada dice cuándo (ck_firmas_invalidacion); una vigente, no.
        Timestamp invalidada = "INVALIDADA".equals(estado)
                ? Timestamp.from(MES.plusMonths(1).atDay(2).atStartOfDay(MADRID).toInstant())
                : null;
        jdbc.update("""
                INSERT INTO firmas_mensuales (empresa_id, usuario_id, anio, mes, hash, version_huella, jornadas,
                                              segundos_netos, estado, firmada_en, invalidada_en)
                VALUES (?, ?, ?, ?, ?, 1, 2, 57600, ?, ?, ?)
                """, empresa.getId(), quien.getId(), MES.getYear(), MES.getMonthValue(), "b".repeat(64), estado,
                firmada, invalidada);
    }

    private User persona(String nombre, Role rol) {
        return userRepository.save(User.builder().nombre(nombre).apellidos("Pruebas")
                .email(nombre.toLowerCase() + "." + System.nanoTime() + "@inspeccion.test").contrasena("x")
                .rol(rol).empresa(empresa).activo(true).build());
    }

    private long consultasDe(Runnable llamada) {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();
        llamada.run();
        return estadisticas.getPrepareStatementCount();
    }
}
