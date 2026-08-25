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
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> "nxtime");
        registry.add("spring.datasource.password", () -> "nxtime");
    }

    @Test
    void contextLoads() {
    }
}
