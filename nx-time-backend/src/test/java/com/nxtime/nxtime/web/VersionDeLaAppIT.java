package com.nxtime.nxtime.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.security.VersionDeLaAppFilter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La versión mínima de la app, de punta a punta (ADR 064): el filtro está en la
 * cadena de seguridad y va antes que la autenticación, así que una app retirada
 * recibe 426 y no un 401 que la mandaría a entrar otra vez en bucle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
class VersionDeLaAppIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "version_app_it_" + System.nanoTime();
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
        registry.add("application.app.version-minima", () -> "30");
        registry.add("application.app.ids-retirados", () -> "com.viejo.app");
    }

    @Autowired
    private TestRestTemplate rest;

    private ResponseEntity<String> pedir(HttpMethod metodo, String ruta, String paquete, String version, String cuerpo) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        if (paquete != null) {
            cabeceras.set(VersionDeLaAppFilter.CABECERA_PAQUETE, paquete);
            cabeceras.set(VersionDeLaAppFilter.CABECERA_VERSION, version);
        }
        return rest.exchange(ruta, metodo, new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    @Test
    @DisplayName("Una app vieja recibe 426 antes que el 401 de no llevar sesión; la web, el 401 de siempre")
    void antesQueLaAutenticacion() {
        ResponseEntity<String> vieja = pedir(HttpMethod.GET, "/api/v1/fichaje/activo", "com.nxtime.app", "29", null);
        assertThat(vieja.getStatusCode().value()).isEqualTo(426);
        assertThat(vieja.getBody()).contains(VersionDeLaAppFilter.DESACTUALIZADA).contains("urlDescarga");

        ResponseEntity<String> retirada = pedir(HttpMethod.GET, "/api/v1/fichaje/activo", "com.viejo.app", "99", null);
        assertThat(retirada.getStatusCode().value()).isEqualTo(426);
        assertThat(retirada.getBody()).contains(VersionDeLaAppFilter.RETIRADA);

        assertThat(pedir(HttpMethod.GET, "/api/v1/fichaje/activo", "com.nxtime.app", "30", null)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(pedir(HttpMethod.GET, "/api/v1/fichaje/activo", null, null, null)
                .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("Una app retirada puede seguir entrando: el login no le da 426")
    void elLoginSigueAbierto() {
        ResponseEntity<String> login = pedir(HttpMethod.POST, "/auth/login", "com.viejo.app", "1",
                "{\"email\":\"nadie@version.test\",\"password\":\"no-es-esta\"}");
        assertThat(login.getStatusCode().value()).isNotEqualTo(426);
    }
}
