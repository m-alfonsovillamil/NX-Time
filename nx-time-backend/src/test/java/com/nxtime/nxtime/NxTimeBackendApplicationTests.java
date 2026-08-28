package com.nxtime.nxtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Smoke test: solo comprueba que el contexto de Spring arranca. Desde
 * la Fase 3 necesita su propio PostgreSQL: ya no hay un datasource
 * SQLite por defecto al que caer si no se especifica ninguno. Ver el
 * comentario detallado de ApiContractTest sobre por qué esto crea su
 * propia base de datos en el Postgres de docker-compose.yml en vez de
 * usar Testcontainers.
 *
 * Requisito para ejecutar esta clase: `docker compose up -d postgres`.
 */
@SpringBootTest
class NxTimeBackendApplicationTests {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "smoke_test_" + System.nanoTime();
        String adminUrl = "jdbc:postgresql://localhost:5433/nxtime";
        try (Connection admin = DriverManager.getConnection(adminUrl, "nxtime", "nxtime");
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + testDb);
        }

        String testUrl = "jdbc:postgresql://localhost:5433/" + testDb;
        // Fase 8: Flyway migra como "nxtime" (necesita DDL); la app en
        // runtime se conecta como "nxtime_app" (sin privilegios de
        // superusuario -- ver application-dev.yml y
        // docker/postgres/init-app-role.sql para el porqué). "nxtime_app"
        // ya existe a nivel de clúster (los roles de Postgres no son
        // por base de datos), y V3__audit_trail.sql le concede los
        // privilegios que necesita sobre esta base nueva al migrar.
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> "nxtime_app");
        registry.add("spring.datasource.password", () -> "nxtime_app");
        registry.add("spring.flyway.url", () -> testUrl);
        registry.add("spring.flyway.user", () -> "nxtime");
        registry.add("spring.flyway.password", () -> "nxtime");
    }

    @Test
    void contextLoads() {
    }
}
