package com.nxtime.nxtime.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * V23 no puede cambiar ni un segundo de los informes de horas por proyecto.
 *
 * Migra una base hasta V22, siembra jornadas y asignaciones, saca las horas
 * por proyecto con la consulta de ANTES (unir jornada con asignación vigente),
 * migra a V23 y las saca con la de AHORA (sumar imputaciones). Tienen que
 * coincidir exactamente.
 *
 * Sin Spring a propósito: hace falta parar la migración a mitad, y un
 * contexto de Spring la aplicaría entera al arrancar.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
class ProjectAllocationMigrationIT {

    private static final String ANTES = """
            SELECT p.codigo,
                   COALESCE(SUM(EXTRACT(EPOCH FROM (r.hora_salida - r.hora_entrada))
                       - r.segundos_pausa_acumulados), 0)::BIGINT AS segundos
            FROM registros r
            JOIN asignaciones_proyecto a
              ON a.usuario_id = r.usuario_id
             AND (r.hora_entrada AT TIME ZONE 'Europe/Madrid')::date >= a.fecha_inicio
             AND (r.hora_entrada AT TIME ZONE 'Europe/Madrid')::date <= COALESCE(a.fecha_fin, DATE 'infinity')
            JOIN proyectos p ON p.id = a.proyecto_id
            WHERE r.anulado = false AND r.hora_salida IS NOT NULL
            GROUP BY p.codigo ORDER BY p.codigo
            """;

    private static final String AHORA = """
            SELECT p.codigo, COALESCE(SUM(i.segundos), 0)::BIGINT AS segundos
            FROM imputaciones_proyecto i
            JOIN registros r ON r.id = i.registro_id
            JOIN proyectos p ON p.id = i.proyecto_id
            WHERE r.anulado = false
            GROUP BY p.codigo ORDER BY p.codigo
            """;

    @Test
    @DisplayName("Las horas por proyecto son las mismas antes y después de V23")
    void lasHorasNoCambian() throws Exception {
        String db = "migracion_imputaciones_it_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection("jdbc:postgresql://localhost:5433/nxtime", "nxtime", "nxtime");
             Statement st = admin.createStatement()) {
            st.execute("CREATE DATABASE " + db);
        }
        String url = "jdbc:postgresql://localhost:5433/" + db;
        Flyway.configure().dataSource(url, "nxtime", "nxtime").target("22").load().migrate();

        Map<String, Long> antes;
        try (Connection c = DriverManager.getConnection(url, "nxtime", "nxtime"); Statement st = c.createStatement()) {
            st.execute("INSERT INTO empresas (id, nombre) VALUES (1, 'Empresa')");
            st.execute("""
                    INSERT INTO usuarios (id, email, nombre, contrasena, rol, empresa_id)
                    VALUES (1, 'ana@test', 'Ana', 'x', 'EMPLEADO', 1), (2, 'javi@test', 'Javi', 'x', 'EMPLEADO', 1)
                    """);
            st.execute("""
                    INSERT INTO proyectos (id, empresa_id, codigo, nombre, fecha_inicio)
                    VALUES (1, 1, 'NX-CORE', 'Core', '2026-01-01'), (2, 1, 'NX-APP', 'App', '2026-01-01')
                    """);
            // Ana: NX-CORE hasta el 31/05 y NX-APP desde el 01/06. Javi: NX-APP siempre.
            st.execute("""
                    INSERT INTO asignaciones_proyecto (empresa_id, usuario_id, proyecto_id, fecha_inicio, fecha_fin)
                    VALUES (1, 1, 1, '2026-01-01', '2026-05-31'), (1, 1, 2, '2026-06-01', NULL),
                           (1, 2, 2, '2026-01-01', NULL)
                    """);
            st.execute("""
                    INSERT INTO registros (usuario_id, empresa_id, hora_entrada, hora_salida, segundos_pausa_acumulados, anulado)
                    VALUES
                      -- 8 h con 30 min de pausa, en mayo: NX-CORE
                      (1, 1, '2026-05-20T07:00:00Z', '2026-05-20T15:00:00Z', 1800, false),
                      -- 31/05 a las 23:30 en Madrid (21:30 UTC): todavía NX-CORE
                      (1, 1, '2026-05-31T21:30:00Z', '2026-06-01T01:30:00Z', 0, false),
                      -- 01/06 a las 00:30 en Madrid (22:30 UTC del 31): ya NX-APP
                      (1, 1, '2026-05-31T22:30:00Z', '2026-06-01T02:30:00Z', 0, false),
                      -- anulada: no cuenta ni antes ni después
                      (1, 1, '2026-06-03T07:00:00Z', '2026-06-03T15:00:00Z', 0, true),
                      -- abierta: no cuenta
                      (2, 1, '2026-06-04T07:00:00Z', NULL, 0, false),
                      -- Javi en NX-APP
                      (2, 1, '2026-06-02T06:00:00Z', '2026-06-02T13:17:43Z', 600, false),
                      -- antes de que Ana tuviera proyecto: sin imputar
                      (1, 1, '2025-12-15T07:00:00Z', '2025-12-15T15:00:00Z', 0, false)
                    """);
            antes = sumar(st.executeQuery(ANTES));
        }

        Flyway.configure().dataSource(url, "nxtime", "nxtime").load().migrate();

        try (Connection c = DriverManager.getConnection(url, "nxtime", "nxtime"); Statement st = c.createStatement()) {
            Map<String, Long> ahora = sumar(st.executeQuery(AHORA));
            assertThat(antes).isNotEmpty();
            assertThat(ahora).isEqualTo(antes);
            // Y a mano, para no fiarse de que las dos consultas se equivoquen igual:
            // NX-CORE = 7 h 30 min + 4 h; NX-APP = 4 h + (7 h 17 min 43 s - 10 min).
            assertThat(ahora).containsEntry("NX-CORE", 27000L + 14400L)
                    .containsEntry("NX-APP", 14400L + 26263L - 600L);
        }
    }

    private static Map<String, Long> sumar(ResultSet filas) throws Exception {
        Map<String, Long> resultado = new LinkedHashMap<>();
        while (filas.next()) {
            resultado.put(filas.getString("codigo"), filas.getLong("segundos"));
        }
        return resultado;
    }
}
