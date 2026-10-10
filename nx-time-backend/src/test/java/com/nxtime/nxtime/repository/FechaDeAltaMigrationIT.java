package com.nxtime.nxtime.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * V41 le pone fecha de alta a las empresas que ya existían (ADR 040).
 *
 * No había fecha, así que se deduce: lo más antiguo que quede de cada empresa.
 * Y la que no ha dejado rastro se queda sin ella, que es mejor que inventarla.
 *
 * Sin Spring a propósito, como {@link ProjectAllocationMigrationIT}: hace falta
 * parar la migración en V40 para sembrar empresas «de antes».
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
class FechaDeAltaMigrationIT {

    @Test
    @DisplayName("Cada empresa de antes se queda con su rastro más antiguo, y la que no tiene ninguno, sin fecha")
    void lasEmpresasDeAntes() throws Exception {
        String db = "migracion_fecha_alta_it_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection("jdbc:postgresql://localhost:5433/nxtime", "nxtime", "nxtime");
             Statement st = admin.createStatement()) {
            st.execute("CREATE DATABASE " + db);
        }
        String url = "jdbc:postgresql://localhost:5433/" + db;
        Flyway.configure().dataSource(url, "nxtime", "nxtime").target("40").load().migrate();

        try (Connection c = DriverManager.getConnection(url, "nxtime", "nxtime"); Statement st = c.createStatement()) {
            st.execute("""
                    INSERT INTO empresas (id, nombre) VALUES
                      (1, 'La que ficha'), (2, 'La que solo ha entrado'), (3, 'La que solo se registró'),
                      (4, 'La que no ha hecho nada'), (5, 'La que no tiene a nadie')
                    """);
            st.execute("""
                    INSERT INTO usuarios (id, email, nombre, contrasena, rol, empresa_id) VALUES
                      (1, 'ana@uno.test', 'Ana', 'x', 'ADMIN', 1), (2, 'javi@uno.test', 'Javi', 'x', 'EMPLEADO', 1),
                      (3, 'eva@dos.test', 'Eva', 'x', 'ADMIN', 2), (4, 'luis@tres.test', 'Luis', 'x', 'ADMIN', 3),
                      (5, 'sara@cuatro.test', 'Sara', 'x', 'ADMIN', 4)
                    """);
            // La 1: Javi fichó en marzo, antes de que nadie abriese sesión.
            st.execute("""
                    INSERT INTO registros (usuario_id, empresa_id, hora_entrada, hora_salida, anulado) VALUES
                      (2, 1, '2026-03-10T08:00:00Z', '2026-03-10T16:00:00Z', false),
                      (1, 1, '2026-05-02T08:00:00Z', '2026-05-02T16:00:00Z', false)
                    """);
            st.execute("""
                    INSERT INTO refresh_tokens (token_hash, usuario_id, familia, origen, expira_en, revocado, creado_en)
                    VALUES
                      (repeat('a', 64), 1, gen_random_uuid(), 'WEB', '2027-01-01T00:00:00Z', false, '2026-04-01T09:00:00Z'),
                      -- La 2: dos sesiones, se queda con la primera.
                      (repeat('b', 64), 3, gen_random_uuid(), 'ANDROID', '2027-01-01T00:00:00Z', false, '2026-06-20T09:00:00Z'),
                      (repeat('c', 64), 3, gen_random_uuid(), 'WEB', '2027-01-01T00:00:00Z', false, '2026-06-05T09:00:00Z')
                    """);
            // La 3: a su ADMIN le llegó un código y nunca llegó a entrar.
            st.execute("""
                    INSERT INTO codigos_acceso (usuario_id, tipo, codigo_hash, creado_en, expira_en)
                    VALUES (4, 'ALTA', 'x', '2026-08-15T12:00:00Z', '2026-08-15T12:30:00Z')
                    """);
        }

        Flyway.configure().dataSource(url, "nxtime", "nxtime").load().migrate();

        Map<Long, Instant> altas = new HashMap<>();
        try (Connection c = DriverManager.getConnection(url, "nxtime", "nxtime"); Statement st = c.createStatement();
             ResultSet filas = st.executeQuery("SELECT id, creada_en FROM empresas")) {
            while (filas.next()) {
                altas.put(filas.getLong("id"),
                        filas.getTimestamp("creada_en") == null ? null : filas.getTimestamp("creada_en").toInstant());
            }
        }

        assertThat(altas).hasSize(5);
        assertThat(altas.get(1L)).isEqualTo(Instant.parse("2026-03-10T08:00:00Z"));
        assertThat(altas.get(2L)).isEqualTo(Instant.parse("2026-06-05T09:00:00Z"));
        assertThat(altas.get(3L)).isEqualTo(Instant.parse("2026-08-15T12:00:00Z"));
        assertThat(altas.get(4L)).isNull();
        assertThat(altas.get(5L)).isNull();
    }
}
