package com.nxtime.nxtime.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * CORS y cabeceras de seguridad, comprobadas sobre peticiones HTTP reales
 * (Fase A10).
 *
 * Tiene que ser {@code RANDOM_PORT} y no un slice de MockMvc: lo que se está
 * probando son las cabeceras que salen por el socket y cómo se comporta el
 * preflight, y eso solo existe cuando hay un servidor de verdad delante.
 *
 * <b>Qué había antes.</b> La configuración de CORS declaraba orígenes y
 * métodos, pero no {@code exposedHeaders}, así que un navegador recibía
 * {@code Content-Disposition} y no podía leerla: las descargas de informes y
 * adjuntos habrían perdido el nombre del fichero sin que nada fallara. Tampoco
 * admitía {@code X-Request-Id} en el preflight, aunque el backend la entiende
 * desde hace tiempo. Y no había ninguna cabecera de seguridad configurada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsYCabecerasIT {

    private static final String ORIGEN_PERMITIDO = "https://nxtime-web.onrender.com";
    private static final String ORIGEN_AJENO = "https://sitio-de-otro.example";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "cors_it_" + System.nanoTime();
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
        // Un origen concreto, como en producción. En dev vale "*", pero probar
        // con "*" no comprobaría que la lista blanca funciona.
        registry.add("application.security.cors.allowed-origins", () -> ORIGEN_PERMITIDO);
    }

    @Autowired
    private TestRestTemplate rest;

    /** Un preflight tal y como lo manda un navegador antes de un fetch. */
    private ResponseEntity<String> preflight(String origen, String cabeceraPedida) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set(HttpHeaders.ORIGIN, origen);
        cabeceras.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        if (cabeceraPedida != null) {
            cabeceras.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, cabeceraPedida);
        }
        return rest.exchange("/api/v1/fichaje/activo", HttpMethod.OPTIONS,
                new HttpEntity<>(cabeceras), String.class);
    }

    @Nested
    @DisplayName("CORS")
    class Cors {

        @Test
        @DisplayName("Un origen de la lista blanca pasa el preflight")
        void origenPermitido_pasa() {
            ResponseEntity<String> respuesta = preflight(ORIGEN_PERMITIDO, null);

            assertThat(respuesta.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(respuesta.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ORIGEN_PERMITIDO);
        }

        @Test
        @DisplayName("Un origen que no está en la lista se rechaza")
        void origenAjeno_seRechaza() {
            ResponseEntity<String> respuesta = preflight(ORIGEN_AJENO, null);

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        /**
         * La que arregla las descargas.
         *
         * Sin {@code Access-Control-Expose-Headers}, el navegador recibe
         * {@code Content-Disposition} pero el JavaScript no la ve, así que un
         * informe de Excel se guardaría como "download" en vez de con su
         * nombre. No falla nada: simplemente sale mal.
         */
        @Test
        @DisplayName("El navegador puede leer Content-Disposition, X-Request-Id y Retry-After")
        void exponeLasCabecerasQueElClienteNecesitaLeer() {
            ResponseEntity<String> respuesta = preflight(ORIGEN_PERMITIDO, null);

            assertThat(respuesta.getHeaders().getAccessControlExposeHeaders())
                    .contains("Content-Disposition", "X-Request-Id", "Retry-After");
        }

        @Test
        @DisplayName("X-Request-Id se admite en el preflight: el backend ya la entendía")
        void admiteXRequestIdEnElPreflight() {
            // CorrelationIdFilter la lee desde hace tiempo, pero al no estar en
            // allowedHeaders el preflight la rechazaba y la petición no salía.
            ResponseEntity<String> respuesta = preflight(ORIGEN_PERMITIDO, "X-Request-Id");

            assertThat(respuesta.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(respuesta.getHeaders().getAccessControlAllowHeaders())
                    .contains("X-Request-Id");
        }

        @Test
        @DisplayName("El preflight se cachea: con el arranque en frío de Render, duplicar peticiones sale caro")
        void elPreflightSeCachea() {
            ResponseEntity<String> respuesta = preflight(ORIGEN_PERMITIDO, null);

            assertThat(respuesta.getHeaders().getAccessControlMaxAge()).isPositive();
        }
    }

    @Nested
    @DisplayName("Cabeceras de seguridad")
    class Cabeceras {

        private HttpHeaders cabecerasDe(String ruta) {
            return rest.getForEntity(ruta, String.class).getHeaders();
        }

        @Test
        @DisplayName("La API devuelve CSP, HSTS, Referrer-Policy, Permissions-Policy y X-Frame-Options")
        void laApiLlevaLasCabecerasDeSeguridad() {
            // Sin token: da 401, y las cabeceras tienen que venir igual. Una
            // política que solo se aplica a las respuestas con éxito no
            // protege nada.
            HttpHeaders cabeceras = cabecerasDe("/api/v1/fichaje/activo");

            assertThat(cabeceras.getFirst("Content-Security-Policy"))
                    .contains("default-src 'none'")
                    .contains("frame-ancestors 'none'");
            assertThat(cabeceras.getFirst("Strict-Transport-Security")).contains("max-age=31536000");
            assertThat(cabeceras.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
            assertThat(cabeceras.getFirst("Permissions-Policy")).contains("geolocation=()");
            assertThat(cabeceras.getFirst("X-Frame-Options")).isEqualTo("DENY");
        }

        /**
         * La regresión clásica al apretar la CSP en una API que además publica
         * su documentación: {@code default-src 'none'} deja Swagger UI en
         * blanco, y nadie se entera hasta que alguien intenta abrirlo.
         */
        @Test
        @DisplayName("Swagger UI sigue cargando: su CSP es la suya, no la de la API")
        void swaggerSigueCargando() {
            ResponseEntity<String> respuesta = rest.getForEntity("/v3/api-docs", String.class);

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(respuesta.getBody()).contains("\"openapi\"");

            String csp = respuesta.getHeaders().getFirst("Content-Security-Policy");
            assertThat(csp).doesNotContain("default-src 'none'");
            // Pero sigue sin poder empotrarse en un iframe ajeno.
            assertThat(csp).contains("frame-ancestors 'none'");
        }

        @Test
        @DisplayName("El health check sigue respondiendo: Render lo consulta antes de que exista ningún token")
        void elHealthCheckSigueVivo() {
            ResponseEntity<String> respuesta = rest.getForEntity("/actuator/health", String.class);

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
