package com.nxtime.nxtime.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La comprobación manual de la traza, pedida por muchos a la vez (ADR 039).
 *
 * Es lo que se podía usar para tumbar el servicio: cada petición hacía su
 * propio recorrido de la cadena entera, con una conexión del pool mientras
 * durase. Aquí se comprueba, contra PostgreSQL y con el pool de verdad, que
 * doce peticiones a la vez son UN recorrido, y que las que esperan no tienen
 * conexión cogida. Con mocks no hay pool que agotar.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class VerificacionManualConcurrenteIT {

    private static final int PETICIONES = 12;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "verificacion_manual_it_" + System.nanoTime();
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

        // Una fila por bloque: así el recorrido son tantas consultas como
        // movimientos y dura lo bastante para que las demás peticiones lleguen
        // mientras está en marcha, sin sembrar miles de filas.
        registry.add("application.auditoria.filas-por-bloque", () -> 1);
        // Medio minuto, como en producción: lo que se prueba es que se comparte.
        registry.add("application.auditoria.vigencia-del-recorrido", () -> "30s");
        // El tope de duracion de una consulta, bajado de 30 a 2 segundos para
        // poder probar que corta sin esperar medio minuto.
        registry.add("DB_STATEMENT_TIMEOUT", () -> "2s");
    }

    @Autowired
    private VerificadorDeAuditoria verificador;
    @Autowired
    private TimeEntryService timeEntryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * El tope va en una linea de configuracion (connection-init-sql), y una
     * linea asi es facil creer que funciona sin que lo haga: aqui se comprueba
     * que cada conexion del pool lo lleva puesto y que corta de verdad.
     */
    @Test
    @DisplayName("Ninguna consulta dura mas que el tope: las conexiones del pool lo llevan puesto y una consulta lenta se corta")
    void unaConsultaLentaSeCorta() {
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("2s");

        long antes = System.nanoTime();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.queryForObject("SELECT pg_sleep(10)", Object.class))
                .hasMessageContaining("statement timeout");
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - antes)).isLessThan(java.time.Duration.ofSeconds(6));

        // Y la conexion vuelve al pool utilizable.
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    /** Una empresa con sus jornadas: cuatro movimientos de auditoría por jornada. */
    private long empresaConJornadas(String nombre, int jornadas) {
        Company empresa = companyRepository.save(Company.builder().nombre(nombre + " " + System.nanoTime()).build());
        for (int i = 0; i < jornadas; i++) {
            String email = nombre + i + "-" + System.nanoTime() + "@nxtime.test";
            userRepository.save(User.builder().email(email).nombre("Empleado").contrasena("hash")
                    .rol(Role.EMPLEADO).empresa(empresa).activo(true).build());
            timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.INICIO));
            timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.PAUSA_INICIO));
            timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.PAUSA_FIN));
            timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.FIN));
        }
        return empresa.getId();
    }

    @Test
    @DisplayName("Doce comprobaciones a la vez son un solo recorrido, cada empresa recibe lo suyo y las que esperan no ocupan conexión")
    void muchasALaVez_unSoloRecorrido() throws Exception {
        long a = empresaConJornadas("a", 60);
        long b = empresaConJornadas("b", 40);
        long recorridosAntes = verificador.recorridosCompletos();
        var pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();

        // Un vigilante que apunta, mientras dura, cuántas conexiones hay en uso
        // y cuántas peticiones están esperando su turno.
        AtomicInteger maximoDeConexiones = new AtomicInteger();
        AtomicInteger maximoEsperando = new AtomicInteger();
        AtomicBoolean vigilando = new AtomicBoolean(true);
        Thread vigilante = new Thread(() -> {
            while (vigilando.get()) {
                maximoDeConexiones.accumulateAndGet(pool.getActiveConnections(), Math::max);
                maximoEsperando.accumulateAndGet(verificador.esperandoTurno(), Math::max);
                Thread.onSpinWait();
            }
        });
        vigilante.start();

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(PETICIONES);
        List<Future<AuditIntegrityResponse>> deA = new ArrayList<>();
        List<Future<AuditIntegrityResponse>> deB = new ArrayList<>();
        try {
            for (int i = 0; i < PETICIONES; i++) {
                long empresa = i % 2 == 0 ? a : b;
                (i % 2 == 0 ? deA : deB).add(hilos.submit(() -> {
                    salida.await();
                    return verificador.verificarPara(empresa);
                }));
            }
            salida.countDown();

            for (Future<AuditIntegrityResponse> respuesta : deA) {
                AuditIntegrityResponse r = respuesta.get(60, TimeUnit.SECONDS);
                assertThat(r.intacta()).isTrue();
                assertThat(r.movimientos()).isEqualTo(240);
            }
            for (Future<AuditIntegrityResponse> respuesta : deB) {
                assertThat(respuesta.get(60, TimeUnit.SECONDS).movimientos()).isEqualTo(160);
            }
        } finally {
            vigilando.set(false);
            vigilante.join(5_000);
            hilos.shutdownNow();
        }

        // Lo que se arregla: antes eran doce recorridos.
        assertThat(verificador.recorridosCompletos() - recorridosAntes).isEqualTo(1);
        // La prueba vale porque de verdad coincidieron: hubo peticiones esperando.
        assertThat(maximoEsperando.get()).as("peticiones esperando a la vez").isGreaterThanOrEqualTo(2);
        // Y esperar no ocupa una conexión: en uso, la de quien recorre.
        assertThat(maximoDeConexiones.get()).as("conexiones del pool en uso a la vez").isLessThanOrEqualTo(2);

        // Pedirlo otra vez dentro del medio minuto tampoco vuelve a recorrer.
        assertThat(verificador.verificarPara(a).movimientos()).isEqualTo(240);
        assertThat(verificador.recorridosCompletos() - recorridosAntes).isEqualTo(1);
    }
}
