package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Los plazos de conservación (plan J, fase J5), contra PostgreSQL real y como
 * {@code nxtime_app}: si le faltara un permiso de DELETE, o una clave no se
 * hubiera tenido en cuenta, saldría aquí y no una noche en producción.
 *
 * De cada cosa se siembra una fila vieja y una reciente: lo que se comprueba es
 * que se va justo la vieja.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class ConservacionDeDatosIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "conservacion_it_" + System.nanoTime();
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
    private ConservacionDeDatos conservacion;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static final Instant AHORA = Instant.now();

    private static Timestamp hace(long dias) {
        return Timestamp.from(AHORA.minus(Duration.ofDays(dias)));
    }

    private User persona(Company empresa, String nombre, boolean activo, Instant baja) {
        return userRepository.save(User.builder().nombre(nombre).apellidos("Pruebas")
                .email(nombre.toLowerCase() + System.nanoTime() + "@conservacion.test").contrasena("x")
                .rol(Role.EMPLEADO).empresa(empresa).activo(activo).fechaBaja(baja).build());
    }

    private int cuantas(String sql, Object... argumentos) {
        Integer n = jdbc.queryForObject(sql, Integer.class, argumentos);
        return n == null ? 0 : n;
    }

    private void sesion(long usuarioId, UUID familia, Timestamp expira) {
        jdbc.update("""
                INSERT INTO refresh_tokens (token_hash, usuario_id, familia, origen, expira_en, revocado, creado_en)
                VALUES (?, ?, ?, 'WEB', ?, false, ?)
                """, UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                usuarioId, familia, expira, hace(60));
    }

    @Test
    @DisplayName("Se va lo que ha pasado su plazo y se queda lo reciente, de cada cosa")
    void cadaCosaConSuPlazo() {
        Company empresa = companyRepository.save(Company.builder().nombre("Conservación " + System.nanoTime()).build());
        User ana = persona(empresa, "Ana", true, null);

        // Sesiones: una familia entera caducada hace 40 días (con un token
        // rotado que apunta a su sucesor) y otra con uno todavía vivo.
        UUID vieja = UUID.randomUUID();
        sesion(ana.getId(), vieja, hace(45));
        sesion(ana.getId(), vieja, hace(40));
        jdbc.update("""
                UPDATE refresh_tokens SET rotado_en = ?, sustituido_por_id =
                  (SELECT max(id) FROM refresh_tokens WHERE familia = ?)
                 WHERE id = (SELECT min(id) FROM refresh_tokens WHERE familia = ?)
                """, hace(44), vieja, vieja);
        UUID viva = UUID.randomUUID();
        sesion(ana.getId(), viva, hace(45));
        sesion(ana.getId(), viva, Timestamp.from(AHORA.plus(Duration.ofDays(5))));

        jdbc.update("INSERT INTO codigos_acceso (usuario_id, tipo, codigo_hash, creado_en, expira_en, usado_en) VALUES (?, 'ALTA', 'x', ?, ?, ?)",
                ana.getId(), hace(101), hace(100), hace(100));
        jdbc.update("INSERT INTO codigos_acceso (usuario_id, tipo, codigo_hash, creado_en, expira_en) VALUES (?, 'ALTA', 'x', ?, ?)",
                ana.getId(), Timestamp.from(AHORA), Timestamp.from(AHORA.plus(1, ChronoUnit.HOURS)));

        jdbc.update("INSERT INTO avisos (empresa_id, destinatario_id, tipo, titulo, cuerpo, creado_en) VALUES (?, ?, 'BIENVENIDA', 'Viejo', 'x', ?)",
                empresa.getId(), ana.getId(), hace(400));
        jdbc.update("INSERT INTO avisos (empresa_id, destinatario_id, tipo, titulo, cuerpo, creado_en) VALUES (?, ?, 'BIENVENIDA', 'Nuevo', 'x', ?)",
                empresa.getId(), ana.getId(), hace(1));

        jdbc.update("INSERT INTO dispositivos_push (usuario_id, plataforma, token, registrado_en, visto_en) VALUES (?, 'WEB', ?, ?, ?)",
                ana.getId(), "viejo-" + System.nanoTime(), hace(500), hace(400));
        jdbc.update("INSERT INTO dispositivos_push (usuario_id, plataforma, token, registrado_en, visto_en) VALUES (?, 'WEB', ?, ?, ?)",
                ana.getId(), "nuevo-" + System.nanoTime(), hace(500), hace(1));

        jdbc.update("INSERT INTO ejecuciones_tarea (tarea, inicio, fin, resultado) VALUES ('CIERRE_JORNADAS', ?, ?, 'OK')",
                hace(400), hace(400));

        // Quien lleva cinco años de baja y fichó por última vez hace cinco
        // años, y quien solo lleva dos.
        User antigua = persona(empresa, "Antigua", false, AHORA.minus(Duration.ofDays(5 * 366)));
        jdbc.update("INSERT INTO registros (usuario_id, empresa_id, hora_entrada, hora_salida, anulado) VALUES (?, ?, ?, ?, false)",
                antigua.getId(), empresa.getId(), hace(5 * 366 + 10), hace(5 * 366 + 10));
        User reciente = persona(empresa, "Reciente", false, AHORA.minus(Duration.ofDays(2 * 365)));

        Map<String, Integer> hecho = conservacion.aplicar(AHORA);

        assertThat(cuantas("SELECT count(*) FROM refresh_tokens WHERE familia = ?", vieja)).isZero();
        assertThat(cuantas("SELECT count(*) FROM refresh_tokens WHERE familia = ?", viva)).isEqualTo(2);
        assertThat(cuantas("SELECT count(*) FROM codigos_acceso WHERE usuario_id = ?", ana.getId())).isEqualTo(1);
        assertThat(cuantas("SELECT count(*) FROM avisos WHERE destinatario_id = ? AND titulo = 'Viejo'", ana.getId())).isZero();
        assertThat(cuantas("SELECT count(*) FROM avisos WHERE destinatario_id = ? AND titulo = 'Nuevo'", ana.getId())).isEqualTo(1);
        assertThat(cuantas("SELECT count(*) FROM dispositivos_push WHERE usuario_id = ?", ana.getId())).isEqualTo(1);
        assertThat(cuantas("SELECT count(*) FROM ejecuciones_tarea WHERE inicio < ?", hace(365))).isZero();

        assertThat(userRepository.findById(antigua.getId()).orElseThrow().getEmail()).endsWith("@anonimo.invalid");
        assertThat(userRepository.findById(antigua.getId()).orElseThrow().getNombre()).isEqualTo(PersonalDataEraser.NOMBRE_ANONIMO);
        // Su registro de jornada se queda: lo que se quita es quién era.
        assertThat(cuantas("SELECT count(*) FROM registros WHERE usuario_id = ?", antigua.getId())).isEqualTo(1);
        assertThat(userRepository.findById(reciente.getId()).orElseThrow().getEmail()).doesNotEndWith("@anonimo.invalid");
        assertThat(hecho).containsEntry("personasAnonimizadas", 1).doesNotContainValue(-1);

        // Y repetir no hace nada más: es lo que pasa cada noche.
        assertThat(conservacion.aplicar(AHORA)).containsEntry("personasAnonimizadas", 0);
    }
}
