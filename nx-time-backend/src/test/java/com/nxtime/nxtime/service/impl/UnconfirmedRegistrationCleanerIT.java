package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.notification.EmailSender;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AuthService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * La limpieza de registros de empresa sin confirmar, contra PostgreSQL real y
 * conectado como {@code nxtime_app}: si a la aplicación le faltara el permiso
 * de DELETE en {@code usuarios} o {@code empresas}, o hubiera una clave que no
 * se ha tenido en cuenta, salta aquí y no una madrugada en producción.
 *
 * Los registros se crean con el servicio de verdad ({@code registerManager}),
 * no sembrando filas: así lo que se borra es exactamente lo que deja un
 * registro a medias, con su código de confirmación.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class UnconfirmedRegistrationCleanerIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "registros_it_" + System.nanoTime();
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

    /** El registro manda un correo con el código: aquí no sale ninguno. */
    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private UnconfirmedRegistrationCleaner limpieza;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    /** Registra una empresa y la deja sin confirmar desde hace {@code horas}. */
    private User registroDeHace(String empresa, int horas) {
        String email = "admin" + System.nanoTime() + "@test.example";
        authService.registerManager(new RegisterManagerRequest(empresa, "Alba", "Pruebas", email, "contrasena-de-prueba", "WEB"));
        jdbc.update("UPDATE usuarios SET correo_sin_confirmar_desde = ? WHERE email = ?",
                Timestamp.from(Instant.now().minus(horas, ChronoUnit.HOURS)), email);
        return userRepository.findByEmail(email).orElseThrow();
    }

    private static String nombre(String base) {
        return base + " " + System.nanoTime();
    }

    private int codigosDe(User usuario) {
        Integer cuantos = jdbc.queryForObject("SELECT count(*) FROM codigos_acceso WHERE usuario_id = ?", Integer.class,
                usuario.getId());
        return cuantos == null ? 0 : cuantos;
    }

    @Test
    @DisplayName("Un registro sin confirmar de hace tres días se borra entero y deja libre el nombre")
    void borraElRegistroCaducadoYLiberaElNombre() {
        String empresa = nombre("Talleres López");
        User admin = registroDeHace(empresa, 72);
        assertThat(codigosDe(admin)).isEqualTo(1);
        // Mientras existe, el nombre está cogido: es el problema que esto arregla.
        assertThatThrownBy(() -> authService.registerManager(new RegisterManagerRequest(
                empresa, "Otra", "Persona", "otra" + System.nanoTime() + "@test.example", "contrasena-de-prueba", "WEB")))
                .isInstanceOf(BusinessException.class);

        int borrados = limpieza.borrarCaducados(Instant.now());

        assertThat(borrados).isEqualTo(1);
        assertThat(userRepository.findById(admin.getId())).isEmpty();
        assertThat(companyRepository.findByNombre(empresa)).isEmpty();
        assertThat(codigosDe(admin)).isZero();
        // Y ahora sí se puede registrar.
        authService.registerManager(new RegisterManagerRequest(
                empresa, "Otra", "Persona", "otra" + System.nanoTime() + "@test.example", "contrasena-de-prueba", "WEB"));
        assertThat(companyRepository.findByNombre(empresa)).isPresent();
    }

    @Test
    @DisplayName("Uno de ayer se deja: todavía puede confirmar")
    void noTocaElQueAunEstaEnPlazo() {
        String empresa = nombre("Reciente");
        User admin = registroDeHace(empresa, 24);

        limpieza.borrarCaducados(Instant.now());

        assertThat(userRepository.findById(admin.getId())).isPresent();
        assertThat(companyRepository.findByNombre(empresa)).isPresent();
        assertThat(codigosDe(admin)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una empresa confirmada no se toca, por antigua que sea")
    void noTocaLaEmpresaConfirmada() {
        String empresa = nombre("Confirmada");
        User admin = registroDeHace(empresa, 720);
        jdbc.update("UPDATE usuarios SET correo_sin_confirmar_desde = NULL WHERE id = ?", admin.getId());

        limpieza.borrarCaducados(Instant.now());

        assertThat(userRepository.findById(admin.getId())).isPresent();
        assertThat(companyRepository.findByNombre(empresa)).isPresent();
    }

    @Test
    @DisplayName("Si la empresa tiene a alguien más, no se borra aunque su ADMIN siga sin confirmar")
    void noTocaLaEmpresaConMasGente() {
        String empresa = nombre("Con gente");
        User admin = registroDeHace(empresa, 72);
        jdbc.update("""
                INSERT INTO usuarios (nombre, apellidos, email, contrasena, rol, empresa_id, activo, version)
                SELECT 'Javi', 'Pruebas', ?, contrasena, 'EMPLEADO', empresa_id, TRUE, 0 FROM usuarios WHERE id = ?
                """, "javi" + System.nanoTime() + "@test.example", admin.getId());

        limpieza.borrarCaducados(Instant.now());

        assertThat(userRepository.findById(admin.getId())).isPresent();
        assertThat(companyRepository.findByNombre(empresa)).isPresent();
    }

    @Test
    @DisplayName("Uno que no se puede borrar se deja como estaba y no impide borrar los demás")
    void unoQueNoSePuedeBorrarNoParaElResto() {
        // Una cuenta sin confirmar no debería tener fichajes; si los tuviera,
        // la clave RESTRICT lo impide y no se fuerza.
        String conFichaje = nombre("Con fichaje");
        User raro = registroDeHace(conFichaje, 72);
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(raro).empresa(raro.getEmpresa()).horaEntrada(Instant.now().minus(80, ChronoUnit.HOURS))
                .horaSalida(Instant.now().minus(72, ChronoUnit.HOURS)).build());
        String normal = nombre("Normal");
        User caducado = registroDeHace(normal, 72);

        limpieza.borrarCaducados(Instant.now());

        assertThat(userRepository.findById(raro.getId())).isPresent();
        assertThat(companyRepository.findByNombre(conFichaje)).isPresent();
        // Entero: el código tampoco se ha ido, que iba en la misma transacción.
        assertThat(codigosDe(raro)).isEqualTo(1);
        assertThat(userRepository.findById(caducado.getId())).isEmpty();
        assertThat(companyRepository.findByNombre(normal)).isEmpty();
    }
}
