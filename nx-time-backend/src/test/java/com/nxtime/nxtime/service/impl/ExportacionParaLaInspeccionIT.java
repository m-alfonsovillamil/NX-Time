package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DataAccessResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La exportación para la Inspección (plan J, fase J7), entera y contra
 * PostgreSQL real: se genera el ZIP y se abre para ver qué lleva.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class ExportacionParaLaInspeccionIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "inspeccion_it_" + System.nanoTime();
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

    @Autowired private ExportacionParaLaInspeccion exportacion;
    @Autowired private RegistroDeAccesos accesos;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JdbcTemplate jdbc;

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private User persona(Company empresa, String nombre, Role rol) {
        return userRepository.save(User.builder().nombre(nombre).apellidos("Pruebas")
                .email(nombre.toLowerCase().replace(" ", "") + System.nanoTime() + "@inspeccion.test").contrasena("x")
                .rol(rol).empresa(empresa).activo(true).build());
    }

    private long jornada(User quien, Company empresa, YearMonth mes, int dia) {
        Timestamp entrada = Timestamp.from(mes.atDay(dia).atTime(8, 0).atZone(MADRID).toInstant());
        Timestamp salida = Timestamp.from(mes.atDay(dia).atTime(16, 0).atZone(MADRID).toInstant());
        jdbc.update("INSERT INTO registros (usuario_id, empresa_id, hora_entrada, hora_salida, anulado) VALUES (?, ?, ?, ?, false)",
                quien.getId(), empresa.getId(), entrada, salida);
        return jdbc.queryForObject("SELECT max(id) FROM registros WHERE usuario_id = ?", Long.class, quien.getId());
    }

    private static Map<String, byte[]> abrir(byte[] zip) throws Exception {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry e = entrada.getNextEntry(); e != null; e = entrada.getNextEntry()) {
                entradas.put(e.getName(), entrada.readAllBytes());
            }
        }
        return entradas;
    }

    @Test
    @DisplayName("El ZIP lleva el Excel de cada mes, el PDF de cada persona, la traza con su autor y las firmas")
    void elZipEntero() throws Exception {
        Company empresa = companyRepository.save(Company.builder().nombre("Inspección " + System.nanoTime()).build());
        User rrhh = persona(empresa, "Rrhh", Role.RRHH);
        User ana = persona(empresa, "Ana", Role.EMPLEADO);
        // Dos personas que se llaman igual: el ZIP no puede tener dos entradas iguales.
        User otraAna = persona(empresa, "Ana", Role.EMPLEADO);
        YearMonth enero = YearMonth.of(2026, 1);
        YearMonth febrero = YearMonth.of(2026, 2);
        long registro = jornada(ana, empresa, enero, 12);
        jornada(otraAna, empresa, enero, 13);
        jornada(ana, empresa, febrero, 3);
        jdbc.update("""
                INSERT INTO auditoria_fichaje (registro_id, usuario_id, modificado_por_id, accion, motivo, fecha_hora, hash)
                VALUES (?, ?, ?, 'CORRECCION', 'Olvidó fichar la salida', ?, ?)
                """, registro, ana.getId(), rrhh.getId(),
                Timestamp.from(enero.atDay(14).atTime(9, 0).atZone(MADRID).toInstant()), "h" + System.nanoTime());
        jdbc.update("""
                INSERT INTO firmas_mensuales (empresa_id, usuario_id, anio, mes, hash, version_huella, jornadas, segundos_netos, estado, firmada_en)
                VALUES (?, ?, 2026, 1, ?, 1, 1, 28800, 'VIGENTE', ?)
                """, empresa.getId(), ana.getId(), "a".repeat(64), Timestamp.from(febrero.atDay(1).atStartOfDay(MADRID).toInstant()));

        User actor = userRepository.findById(rrhh.getId()).orElseThrow();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        exportacion.escribir(exportacion.preparar(actor, enero, febrero), salida);
        Map<String, byte[]> zip = abrir(salida.toByteArray());

        assertThat(zip.keySet()).contains(
                "LEEME.txt", "horas-2026-01.xlsx", "horas-2026-02.xlsx",
                "registros/2026-01/ana.pdf", "registros/2026-01/ana-2.pdf", "registros/2026-02/ana.pdf",
                "correcciones.csv", "firmas.csv");
        assertThat(new String(zip.get("registros/2026-01/ana.pdf"), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        // Un .xlsx es un ZIP: empieza por PK.
        assertThat(new String(zip.get("horas-2026-01.xlsx"), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        String correcciones = new String(zip.get("correcciones.csv"), StandardCharsets.UTF_8);
        assertThat(correcciones).contains("CORRECCION").contains("Rrhh Pruebas").contains("Olvidó fichar la salida");
        assertThat(new String(zip.get("firmas.csv"), StandardCharsets.UTF_8)).contains("Ana Pruebas").contains("2026-01").contains("VIGENTE");

        // Queda una sola anotación de la exportación, no una por persona y mes.
        assertThat(accesos.deLaEmpresa(actor, null)).extracting(DataAccessResponse::que)
                .containsOnlyOnce("EXPORTACION")
                .doesNotContain("INFORME_MENSUAL");
    }

    @Test
    @DisplayName("Como mucho doce meses, y no al revés")
    void periodoValido() {
        Company empresa = companyRepository.save(Company.builder().nombre("Periodo " + System.nanoTime()).build());
        User rrhh = persona(empresa, "Rrhh", Role.RRHH);
        assertThatThrownBy(() -> exportacion.preparar(rrhh, YearMonth.of(2026, 1), YearMonth.of(2027, 1)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> exportacion.preparar(rrhh, YearMonth.of(2026, 5), YearMonth.of(2026, 4)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Los nombres de fichero no llevan tildes, espacios ni barras")
    void nombresDeFichero() {
        assertThat(ExportacionParaLaInspeccion.nombreDeFichero("José María Núñez/../x")).isEqualTo("jose-maria-nunez-x");
        assertThat(ExportacionParaLaInspeccion.nombreDeFichero("   ")).isEqualTo("persona");
    }
}
