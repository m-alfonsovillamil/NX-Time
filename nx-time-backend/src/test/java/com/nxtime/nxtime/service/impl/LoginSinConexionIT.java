package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AuthService;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * La contraseña se comprueba sin ninguna conexión a la base cogida (ADR 041).
 *
 * Comprobarla es BCrypt: unos 80 ms de CPU, a propósito. Hasta octubre de 2026
 * el login entero iba en una transacción, así que cada entrada retenía una de
 * las cinco conexiones mientras esperaba su turno de procesador. En la prueba
 * de carga, cien personas entrando en un minuto con poca CPU dejaron el pool
 * con cinco ocupadas y noventa peticiones esperando, y nadie más podía ni
 * fichar (docs/PRUEBA-DE-CARGA.md).
 *
 * Con el contexto de verdad y no con mocks: lo que se comprueba es cómo están
 * puestas las transacciones, y eso solo existe con los proxies de Spring.
 *
 * <b>Se mira el pool, no si hay una transacción.</b> La primera versión de este
 * test preguntaba «¿hay una transacción abierta mientras se comprueba la
 * contraseña?», decía que no, y pasaba con una conexión cogida igualmente:
 * una consulta de repositorio fuera de una transacción deja la suya retenida
 * hasta que termina el método que la rodea. Con trescientas personas entrando
 * a la vez, cada una retenía una mientras pedía otra para guardar la sesión, y
 * el pool de cinco se quedó bloqueado: no entró nadie. Salió al repetir la
 * prueba de carga, no aquí. De ahí el segundo test, con varias entradas a la
 * vez y menos conexiones que entradas.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class LoginSinConexionIT {

    private static final String CONTRASENA = "unaContrasena123";
    private static final int POOL = 2;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "login_sin_conexion_it_" + System.nanoTime();
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
        // Menos conexiones que entradas a la vez, y poca paciencia: si una
        // entrada retuviera una conexión mientras pide otra, el segundo test
        // se quedaría sin ellas y fallaría en segundos, no en 45.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> POOL);
        registry.add("spring.datasource.hikari.connection-timeout", () -> 4000);
    }

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    /** El de verdad, con un espía para mirar en qué condiciones se le llama. */
    @MockitoSpyBean private PasswordEncoder passwordEncoder;

    private User cuenta(String nombre, Instant sinConfirmarDesde) {
        Company empresa = companyRepository.save(Company.builder().nombre("Empresa de " + nombre + System.nanoTime()).build());
        return userRepository.save(User.builder()
                .nombre(nombre).email(nombre.toLowerCase() + System.nanoTime() + "@nxtime.test")
                .contrasena(passwordEncoder.encode(CONTRASENA)).rol(Role.ADMIN).empresa(empresa).activo(true)
                .correoSinConfirmarDesde(sinConfirmarDesde)
                .build());
    }

    private int conexionesCogidas() {
        try {
            return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Apunta, cada vez que se comprueba una contraseña, cuántas conexiones hay cogidas en ese momento. */
    private List<Integer> vigilarLaComprobacion() {
        List<Integer> cogidas = Collections.synchronizedList(new ArrayList<>());
        doAnswer(llamada -> {
            cogidas.add(conexionesCogidas());
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return llamada.callRealMethod();
        }).when(passwordEncoder).matches(any(), any());
        return cogidas;
    }

    @Test
    @DisplayName("Al entrar, la contraseña se comprueba sin ninguna conexión cogida, y la sesión se abre igual")
    void laContrasenaSeCompruebaSinConexion() {
        User ana = cuenta("Ana", null);
        List<Integer> cogidas = vigilarLaComprobacion();

        AuthenticationResponse sesion = authService.login(new LoginRequest(ana.getEmail(), CONTRASENA, null));

        assertThat(cogidas).as("se ha comprobado la contraseña").isNotEmpty();
        assertThat(cogidas).as("y sin una conexión cogida mientras tanto").containsOnly(0);
        assertThat(conexionesCogidas()).as("ni después").isZero();
        // Lo que escribe sí ha quedado: la sesión está en la base.
        assertThat(sesion.token()).isNotBlank();
        assertThat(refreshTokenRepository.findByTokenHash(AuthServiceImpl.hashDe(sesion.refreshToken()))).isPresent();
    }

    @Test
    @DisplayName("Con la contraseña mal tampoco se coge una conexión para comprobarla, y no se abre nada")
    void conLaContrasenaMal() {
        User ana = cuenta("Berta", null);
        long antes = refreshTokenRepository.count();
        List<Integer> cogidas = vigilarLaComprobacion();

        assertThatThrownBy(() -> authService.login(new LoginRequest(ana.getEmail(), "otra-cosa", null)))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(cogidas).isNotEmpty().containsOnly(0);
        assertThat(conexionesCogidas()).isZero();
        assertThat(refreshTokenRepository.count()).isEqualTo(antes);
    }

    /*
     * El fallo que salió ejecutando: más entradas a la vez que conexiones. Si
     * cada una retiene una conexión mientras pide otra, se bloquean entre
     * ellas y no entra ninguna.
     */
    @Test
    @DisplayName("Doce personas entrando a la vez con dos conexiones: entran todas")
    void masEntradasALaVezQueConexiones() throws Exception {
        List<User> personas = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            personas.add(cuenta("Persona" + i, null));
        }

        ExecutorService hilos = Executors.newFixedThreadPool(personas.size());
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<AuthenticationResponse>> entradas = new ArrayList<>();
            for (User persona : personas) {
                entradas.add(hilos.submit(() -> {
                    salida.await();
                    return authService.login(new LoginRequest(persona.getEmail(), CONTRASENA, null));
                }));
            }
            salida.countDown();

            for (Future<AuthenticationResponse> entrada : entradas) {
                assertThat(entrada.get(30, TimeUnit.SECONDS).token()).isNotBlank();
            }
        } finally {
            hilos.shutdownNow();
        }
        assertThat(conexionesCogidas()).isZero();
    }

    /*
     * Lo delicado de sacar el login de su transacción: a quien entra con la
     * contraseña buena pero sin confirmar el correo se le responde 403 Y se le
     * manda otro código. Ese código tiene que quedar guardado aunque la
     * respuesta sea un error; si la excepción deshiciera la transacción, le
     * llegaría un correo con un código que no existe.
     */
    @Test
    @DisplayName("Sin confirmar el correo: 403, sin sesión, y el código nuevo queda guardado")
    void sinConfirmar_elCodigoQuedaGuardado() {
        User carla = cuenta("Carla", Instant.now());
        long sesionesAntes = refreshTokenRepository.count();

        assertThatThrownBy(() -> authService.login(new LoginRequest(carla.getEmail(), CONTRASENA, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM codigos_acceso WHERE usuario_id = ?", Long.class, carla.getId()))
                .as("el código que se le acaba de mandar")
                .isEqualTo(1L);
        assertThat(refreshTokenRepository.count()).isEqualTo(sesionesAntes);
    }
}
