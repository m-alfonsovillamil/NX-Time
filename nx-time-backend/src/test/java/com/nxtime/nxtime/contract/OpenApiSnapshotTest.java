package com.nxtime.nxtime.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * {@code docs/openapi.json} es lo que dice el backend, no lo que alguien copió
 * un día (Fase C1).
 *
 * <h2>El problema</h2>
 *
 * El fichero se regeneraba <b>a mano</b>: arrancar la aplicación, abrir
 * {@code /v3/api-docs} y pegar el resultado. El README lo reconocía en su lista
 * de limitaciones. Un contrato que se copia a mano se queda atrás en cuanto
 * alguien tiene prisa, y nadie se entera hasta que un cliente generado a partir
 * de él llama a un endpoint que ya no existe.
 *
 * Importa ahora porque la web va a generar sus tipos TypeScript desde este
 * fichero: si miente, miente en compilación y con confianza.
 *
 * <h2>Por qué un test y no un plugin</h2>
 *
 * {@code springdoc-openapi-gradle-plugin} levanta una aplicación aparte para
 * pedirle la spec, y aquí eso significa base de datos, perfil propio y un
 * puerto libre. La suite ya levanta el contexto contra PostgreSQL real en
 * {@link ApiContractTest}, así que esto sale gratis y corre en cada
 * {@code ./gradlew check} sin configuración nueva.
 *
 * <h2>Cómo se actualiza</h2>
 *
 * <pre>./gradlew :nx-time-backend:actualizarOpenApi</pre>
 *
 * que es este mismo test con {@code -Dopenapi.escribir=true}: en vez de fallar,
 * sobrescribe el fichero. Después, {@code git diff docs/openapi.json} enseña
 * exactamente qué ha cambiado del contrato — que es la revisión que antes no
 * existía.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiSnapshotTest {

    /** Dónde vive el contrato versionado, desde la raíz del repositorio. */
    private static final Path FICHERO = Path.of("..", "docs", "openapi.json");

    private static final String ESCRIBIR = "openapi.escribir";

    @org.springframework.test.context.DynamicPropertySource
    static void datasourceProperties(org.springframework.test.context.DynamicPropertyRegistry registry)
            throws Exception {
        String testDb = "openapi_snapshot_" + System.nanoTime();
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
    private TestRestTemplate rest;

    /**
     * El mismo JSON escrito siempre igual: claves ordenadas e indentación fija.
     *
     * Sin esto el fichero cambiaría de un día para otro sin que cambiara nada
     * del contrato --springdoc no garantiza el orden de las claves-- y el
     * {@code git diff} dejaría de servir para ver qué se ha tocado de verdad.
     */
    private String normalizar(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .enable(SerializationFeature.INDENT_OUTPUT);
        JsonNode arbol = mapper.readTree(json);
        return mapper.writeValueAsString(arbol) + "\n";
    }

    @Test
    @DisplayName("docs/openapi.json coincide con lo que expone el backend ahora mismo")
    void elContratoVersionadoEstaAlDia() throws Exception {
        String delServidor = normalizar(rest.getForObject("/v3/api-docs", String.class));

        if (Boolean.getBoolean(ESCRIBIR)) {
            Files.writeString(FICHERO, delServidor, StandardCharsets.UTF_8);
            System.out.println("openapi.json actualizado: " + FICHERO.toAbsolutePath().normalize());
            return;
        }

        if (!Files.exists(FICHERO)) {
            fail("No existe %s. Genéralo con: ./gradlew :nx-time-backend:actualizarOpenApi",
                    FICHERO.toAbsolutePath().normalize());
        }

        String versionado = normalizar(Files.readString(FICHERO, StandardCharsets.UTF_8));
        if (!versionado.equals(delServidor)) {
            // Se deja el nuevo a mano para poder compararlos sin tener que
            // volver a levantar nada.
            Path propuesto = Path.of("build", "openapi.json");
            Files.createDirectories(propuesto.getParent());
            Files.writeString(propuesto, delServidor, StandardCharsets.UTF_8);
            fail("El contrato de la API ha cambiado y docs/openapi.json no. "
                    + "Ejecuta:  ./gradlew :nx-time-backend:actualizarOpenApi  "
                    + "y revisa el git diff. El contrato nuevo está en "
                    + propuesto.toAbsolutePath().normalize());
        }
    }

    @Test
    @DisplayName("La spec declara los servidores reales, no el puerto de quien la generó")
    void declaraLosServidores() throws Exception {
        JsonNode spec = new ObjectMapper().readTree(rest.getForObject("/v3/api-docs", String.class));

        JsonNode servidores = spec.get("servers");
        assertThat(servidores).as("la spec tiene que decir contra qué se habla").isNotNull();
        assertThat(servidores.toString())
                .as("producción tiene que estar, y el puerto de una máquina cualquiera no")
                .contains("nxtime-backend.onrender.com")
                .doesNotContain("8099");
    }
}
