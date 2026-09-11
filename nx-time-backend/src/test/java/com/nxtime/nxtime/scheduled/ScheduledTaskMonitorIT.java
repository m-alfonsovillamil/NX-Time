package com.nxtime.nxtime.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.ScheduledTaskResult;
import com.nxtime.nxtime.domain.ScheduledTaskRun;
import com.nxtime.nxtime.repository.ScheduledTaskRunRepository;
import com.nxtime.nxtime.service.TaskMonitorService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Test de INTEGRACIÓN del registro de tareas programadas (paso 5 del
 * piloto), contra PostgreSQL real.
 *
 * Existe aparte de {@code TaskMonitorServiceImplTest} por lo que solo se
 * ve en la base: que la aplicación, conectada como {@code nxtime_app} y no
 * como propietario, puede insertar y actualizar en
 * {@code ejecuciones_tarea}; que los CHECK de V15 aceptan las filas que
 * escribe el código; y que las dos tareas de verdad, con sus transacciones,
 * quedan registradas.
 *
 * Los dos tests comparten base y pueden correr en cualquier orden, así que
 * ninguno da por hecho lo que hizo el otro.
 *
 * Requisito: `docker compose up -d postgres` (ver ApiContractTest).
 */
@SpringBootTest
class ScheduledTaskMonitorIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "task_monitor_it_" + System.nanoTime();
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
    private IncompleteTimeEntryScheduler cierreDeJornadas;
    @Autowired
    private OvertimeScheduler horasExtra;
    @Autowired
    private TaskMonitorService taskMonitorService;
    @Autowired
    private ScheduledTaskRunRepository repository;

    @Test
    @DisplayName("Las dos tareas nocturnas quedan registradas como OK y el estado pasa a verde")
    void lasDosTareas_quedanRegistradas_yElEstadoPasaAVerde() {
        cierreDeJornadas.cerrarJornadasOlvidadas();
        horasExtra.detectarHorasExtra();

        for (ScheduledTask tarea : ScheduledTask.values()) {
            ScheduledTaskRun ultima = repository.findFirstByTareaOrderByInicioDesc(tarea).orElseThrow();
            assertThat(ultima.getResultado()).as(tarea.name()).isEqualTo(ScheduledTaskResult.OK);
            assertThat(ultima.getFin()).as(tarea.name()).isNotNull();
            assertThat(ultima.getDetalle()).as(tarea.name()).isNotBlank();
        }
        assertThat(taskMonitorService.estado().ok()).isTrue();
    }

    @Test
    @DisplayName("Una tarea que falla queda registrada como ERROR con su motivo, y el fallo sigue subiendo")
    void unaTareaQueFalla_quedaRegistradaComoError() {
        assertThatThrownBy(() -> taskMonitorService.ejecutar(ScheduledTask.CIERRE_JORNADAS, () -> {
            throw new IllegalStateException("fallo provocado por el test");
        })).isInstanceOf(IllegalStateException.class);

        ScheduledTaskRun ultima = repository.findFirstByTareaOrderByInicioDesc(ScheduledTask.CIERRE_JORNADAS)
                .orElseThrow();
        assertThat(ultima.getResultado()).isEqualTo(ScheduledTaskResult.ERROR);
        assertThat(ultima.getFin()).isNotNull();
        assertThat(ultima.getDetalle()).isEqualTo("IllegalStateException: fallo provocado por el test");
    }
}
