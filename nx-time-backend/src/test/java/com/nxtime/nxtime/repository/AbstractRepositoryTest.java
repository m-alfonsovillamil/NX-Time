package com.nxtime.nxtime.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base para los {@code @DataJpaTest} de repositorios: mismo principio que
 * {@code ApiContractTest} (ver ese fichero y {@code build.gradle.kts}
 * para el porqué de no usar Testcontainers en esta máquina) -- una base
 * de datos PostgreSQL nueva y aislada por clase de test, creada con JDBC
 * plano contra el Postgres de {@code docker-compose.yml}. Requiere tener
 * {@code docker compose up -d postgres} corriendo antes de lanzar los
 * tests.
 *
 * {@code @AutoConfigureTestDatabase(Replace.NONE)} evita que Spring Boot
 * sustituya el datasource por uno embebido: H2 no está en el classpath a
 * propósito (mentiría sobre el dialecto SQL real -- índices parciales,
 * CHECK constraints... -- ver plan, Fase 5). Flyway aplica el esquema
 * real sobre la base de datos nueva, igual que en producción.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractRepositoryTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5433/nxtime";
    private static final String DB_USER = "nxtime";
    private static final String DB_PASSWORD = "nxtime";

    @DynamicPropertySource
    protected static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "repo_test_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection(ADMIN_URL, DB_USER, DB_PASSWORD);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + testDb);
        }

        String testUrl = "jdbc:postgresql://localhost:5433/" + testDb;
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
    }
}
