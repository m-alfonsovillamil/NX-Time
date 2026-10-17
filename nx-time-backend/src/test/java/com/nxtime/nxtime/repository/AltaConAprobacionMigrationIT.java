package com.nxtime.nxtime.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * V43 pone estado a las empresas que ya existían (ADR 046): activas, salvo las
 * que nadie llegó a confirmar, que pasan a esperar aprobación. Sin eso, quien
 * tuviera un registro a medias de antes entraría sin aprobación al confirmar.
 *
 * Sin Spring, como {@link FechaDeAltaMigrationIT}: hay que parar en V42 para
 * sembrar empresas «de antes».
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
class AltaConAprobacionMigrationIT {

    @Test
    @DisplayName("Las empresas de antes quedan activas, salvo las que nadie confirmó, que quedan pendientes")
    void lasEmpresasDeAntes() throws Exception {
        String db = "migracion_alta_aprobacion_it_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection("jdbc:postgresql://localhost:5433/nxtime", "nxtime", "nxtime");
             Statement st = admin.createStatement()) {
            st.execute("CREATE DATABASE " + db);
        }
        String url = "jdbc:postgresql://localhost:5433/" + db;
        Flyway.configure().dataSource(url, "nxtime", "nxtime").target("42").load().migrate();

        try (Connection c = DriverManager.getConnection(url, "nxtime", "nxtime"); Statement st = c.createStatement()) {
            st.execute("""
                    INSERT INTO empresas (id, nombre) VALUES
                      (1, 'La que funciona'), (2, 'La que nadie confirmó'), (3, 'La que tiene a uno de cada'),
                      (4, 'La que no tiene a nadie')
                    """);
            st.execute("""
                    INSERT INTO usuarios (id, email, nombre, contrasena, rol, empresa_id, correo_sin_confirmar_desde) VALUES
                      (1, 'ana@uno.test', 'Ana', 'x', 'ADMIN', 1, NULL),
                      (2, 'luis@dos.test', 'Luis', 'x', 'ADMIN', 2, '2026-10-16T10:00:00Z'),
                      (3, 'eva@tres.test', 'Eva', 'x', 'ADMIN', 3, NULL),
                      (4, 'javi@tres.test', 'Javi', 'x', 'EMPLEADO', 3, '2026-10-16T10:00:00Z')
                    """);
        }

        Flyway.configure().dataSource(url, "nxtime", "nxtime").load().migrate();

        Map<Long, String> estados = new HashMap<>();
        try (Connection c = DriverManager.getConnection(url, "nxtime", "nxtime"); Statement st = c.createStatement();
             ResultSet filas = st.executeQuery("SELECT id, estado, condiciones_version FROM empresas")) {
            while (filas.next()) {
                estados.put(filas.getLong("id"), filas.getString("estado"));
                // Ninguna de antes ha aceptado nada: se les pide al entrar.
                assertThat(filas.getString("condiciones_version")).isNull();
            }
        }

        assertThat(estados).containsEntry(1L, "ACTIVA")
                .containsEntry(2L, "PENDIENTE")
                .containsEntry(3L, "ACTIVA")
                .containsEntry(4L, "ACTIVA");
    }
}
