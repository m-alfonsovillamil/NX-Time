package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.ScheduledTaskResult;
import com.nxtime.nxtime.domain.ScheduledTaskRun;
import com.nxtime.nxtime.repository.ScheduledTaskRunRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Una tarea programada corre una vez por turno aunque haya varias instancias
 * del servicio (ADR 042), contra PostgreSQL real.
 *
 * Cada «instancia» de aquí es un {@link TaskMonitorServiceImpl} con su propio
 * identificador, todos contra la misma base: es justo lo que comparten dos
 * instancias de verdad. Con mocks no se prueba nada de esto: lo que decide es
 * el cerrojo de PostgreSQL y que una transacción vea lo que confirmó la otra.
 *
 * Requisito: `docker compose up -d postgres` (ver ApiContractTest).
 */
@SpringBootTest
class TurnoDeLasTareasIT {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Las 3:00 y cinco segundos: recién disparado el cierre de jornadas. */
    private static final Instant AHORA = madrid("2026-09-12T03:00:05");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "turno_tareas_it_" + System.nanoTime();
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
    private ScheduledTaskRunRepository repository;
    @Autowired
    private TransactionTemplate transaccion;

    private static Instant madrid(String fechaHora) {
        return LocalDateTime.parse(fechaHora).atZone(MADRID).toInstant();
    }

    @BeforeEach
    void sinEjecuciones() {
        repository.deleteAll();
    }

    private TaskMonitorServiceImpl instancia(String nombre) {
        return new TaskMonitorServiceImpl(repository, Clock.fixed(AHORA, ZoneOffset.UTC), transaccion, nombre);
    }

    private ScheduledTaskRun yaApuntada(ScheduledTask tarea, Instant inicio, ScheduledTaskResult resultado,
            String instancia) {
        return repository.save(ScheduledTaskRun.builder()
                .tarea(tarea).inicio(inicio).fin(resultado == ScheduledTaskResult.EN_CURSO ? null : inicio)
                .resultado(resultado).instancia(instancia).build());
    }

    @Test
    @DisplayName("La segunda instancia que llega al mismo turno no repite la tarea ni deja rastro")
    void laSegundaInstancia_noRepite() {
        AtomicInteger veces = new AtomicInteger();

        instancia("a").ejecutar(ScheduledTask.CIERRE_JORNADAS, () -> "hecha " + veces.incrementAndGet());
        instancia("b").ejecutar(ScheduledTask.CIERRE_JORNADAS, () -> "hecha " + veces.incrementAndGet());

        assertThat(veces).hasValue(1);
        assertThat(repository.findAll()).singleElement().satisfies(ejecucion -> {
            assertThat(ejecucion.getInstancia()).isEqualTo("a");
            assertThat(ejecucion.getResultado()).isEqualTo(ScheduledTaskResult.OK);
        });
    }

    /**
     * El caso de verdad: las instancias disparan la tarea en el mismo
     * instante. Sin el cerrojo, todas miran antes de que ninguna haya apuntado
     * y corren todas.
     *
     * <b>Tantas instancias como conexiones tiene el pool de los tests, con las
     * conexiones ya abiertas, y veinte veces.</b> La primera versión lanzaba
     * ocho de una vez y pasaba también SIN el cerrojo: el pool abre las
     * conexiones de una en una, la primera instancia acababa antes de que las
     * demás tuvieran la suya, y lo que las ponía en fila era eso. Así compiten
     * de verdad, y quitando el cerrojo falla.
     */
    @Test
    @DisplayName("Varias instancias que disparan la tarea a la vez: la hace una sola, veinte veces de veinte")
    void variasALaVez_laHaceUnaSola() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(INSTANCIAS_A_LA_VEZ);
        try {
            abrirTodasLasConexiones(hilos);
            for (int ronda = 1; ronda <= 20; ronda++) {
                repository.deleteAll();
                AtomicInteger veces = new AtomicInteger();
                CyclicBarrier salida = new CyclicBarrier(INSTANCIAS_A_LA_VEZ);
                List<Future<?>> lanzadas = new ArrayList<>();
                for (int i = 0; i < INSTANCIAS_A_LA_VEZ; i++) {
                    TaskMonitorServiceImpl una = instancia("instancia-" + i);
                    lanzadas.add(hilos.submit(() -> {
                        salida.await(30, TimeUnit.SECONDS);
                        una.ejecutar(ScheduledTask.HORAS_EXTRA, () -> "hecha " + veces.incrementAndGet());
                        return null;
                    }));
                }
                for (Future<?> lanzada : lanzadas) {
                    lanzada.get(30, TimeUnit.SECONDS);
                }

                assertThat(veces).as("ronda %d", ronda).hasValue(1);
                assertThat(repository.findAll()).as("ronda %d", ronda).hasSize(1);
            }
        } finally {
            hilos.shutdownNow();
        }
    }

    /** Las del pool de los tests (src/test/resources/application.properties). */
    private static final int INSTANCIAS_A_LA_VEZ = 4;

    /** Todas cogidas a la vez, para que el pool las tenga abiertas cuando empiece la carrera. */
    private void abrirTodasLasConexiones(ExecutorService hilos) throws Exception {
        CyclicBarrier todasCogidas = new CyclicBarrier(INSTANCIAS_A_LA_VEZ);
        List<Future<?>> cogidas = new ArrayList<>();
        for (int i = 0; i < INSTANCIAS_A_LA_VEZ; i++) {
            cogidas.add(hilos.submit(() -> transaccion.execute(estado -> {
                repository.count();
                try {
                    return todasCogidas.await(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            })));
        }
        for (Future<?> cogida : cogidas) {
            cogida.get(60, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Cada tarea tiene su turno: que otra instancia haya hecho una no impide hacer otra")
    void cadaTareaTieneSuTurno() {
        AtomicInteger veces = new AtomicInteger();

        instancia("a").ejecutar(ScheduledTask.CIERRE_JORNADAS, () -> "hecha " + veces.incrementAndGet());
        instancia("b").ejecutar(ScheduledTask.ANONIMIZACION, () -> "hecha " + veces.incrementAndGet());

        assertThat(veces).hasValue(2);
    }

    /**
     * Tras cada despliegue la instancia es otra: si la ejecución de anoche
     * contase, ninguna tarea volvería a correr.
     */
    @Test
    @DisplayName("La ejecución de anoche, de otra instancia, no cuenta para el turno de hoy")
    void laDeAyer_noCuenta() {
        yaApuntada(ScheduledTask.CIERRE_JORNADAS, AHORA.minus(Duration.ofDays(1)), ScheduledTaskResult.OK, "la-de-ayer");
        AtomicInteger veces = new AtomicInteger();

        instancia("a").ejecutar(ScheduledTask.CIERRE_JORNADAS, () -> "hecha " + veces.incrementAndGet());

        assertThat(veces).hasValue(1);
    }

    @Test
    @DisplayName("Una ejecución anterior a V42, sin instancia, no es de nadie y no impide correr")
    void sinInstancia_noCuenta() {
        yaApuntada(ScheduledTask.CIERRE_JORNADAS, AHORA.minusSeconds(3), ScheduledTaskResult.OK, null);
        AtomicInteger veces = new AtomicInteger();

        instancia("a").ejecutar(ScheduledTask.CIERRE_JORNADAS, () -> "hecha " + veces.incrementAndGet());

        assertThat(veces).hasValue(1);
    }

    /**
     * No se repite aunque la otra no haya acabado bien: no se sabe hasta dónde
     * llegó, y varias tareas mandan correos. Se ve en /estado/tareas.
     */
    @Test
    @DisplayName("Si la otra instancia la dejó a medias o en error, tampoco se repite: el turno queda sin hacer, y se ve")
    void aMediasOEnError_tampocoSeRepite() {
        yaApuntada(ScheduledTask.CIERRE_JORNADAS, AHORA.minusSeconds(3), ScheduledTaskResult.EN_CURSO, "se-cayo");
        yaApuntada(ScheduledTask.HORAS_EXTRA, madrid("2026-09-12T03:30:01"), ScheduledTaskResult.ERROR, "fallo");
        AtomicInteger veces = new AtomicInteger();

        instancia("a").ejecutar(ScheduledTask.CIERRE_JORNADAS, () -> "hecha " + veces.incrementAndGet());
        new TaskMonitorServiceImpl(repository, Clock.fixed(madrid("2026-09-12T03:30:02"), ZoneOffset.UTC),
                transaccion, "a")
                .ejecutar(ScheduledTask.HORAS_EXTRA, () -> "hecha " + veces.incrementAndGet());

        assertThat(veces).hasValue(0);
        TaskMonitorServiceImpl despues = new TaskMonitorServiceImpl(
                repository, Clock.fixed(madrid("2026-09-12T05:15:00"), ZoneOffset.UTC), transaccion, "a");
        assertThat(despues.estado().ok()).isFalse();
    }

    @Test
    @DisplayName("La misma instancia sí puede repetir una tarea en el mismo turno")
    void laMismaInstancia_puedeRepetir() {
        AtomicInteger veces = new AtomicInteger();
        TaskMonitorServiceImpl una = instancia("a");

        una.ejecutar(ScheduledTask.CIERRE_JORNADAS, () -> "hecha " + veces.incrementAndGet());
        una.ejecutar(ScheduledTask.CIERRE_JORNADAS, () -> "hecha " + veces.incrementAndGet());

        assertThat(veces).hasValue(2);
    }
}
