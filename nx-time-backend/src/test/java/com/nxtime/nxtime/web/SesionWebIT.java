package com.nxtime.nxtime.web;

import com.nxtime.nxtime.web.support.CodigosEnviados;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La sesión del navegador en cookie, con su CSRF (fase W1, ADR 030), sobre
 * peticiones HTTP reales.
 *
 * {@code RANDOM_PORT} por lo mismo que {@link CorsYCabecerasIT}: lo que se
 * prueba son las cabeceras {@code Set-Cookie} que salen por el socket y lo que
 * pasa con las que entran, y eso solo existe con un servidor de verdad.
 *
 * <b>Por qué importa tanto el CSRF aquí.</b> Una cookie la manda el navegador
 * solo, así que sin él cualquier web podría pedir un refresco en nombre de
 * quien la visita. Es el error clásico al pasar de cabecera a cookie, y el ADR
 * 020 dejó escrito que se pusiera en el mismo PR que la cookie. Estos tests son
 * los que impiden que se quite sin que nadie se entere.
 *
 * Y la otra mitad: <b>la app Android no cambia</b>. Con el refresh en el cuerpo
 * todo funciona como antes, sin cookies ni CSRF.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
@org.springframework.context.annotation.Import(CodigosEnviados.class)
class SesionWebIT {

    private static final String ORIGEN_WEB = "https://nxtime-web.com";
    private static final String ORIGEN_AJENO = "https://sitio-de-otro.example";
    private static final String CONTRASENA = "unaContrasena123";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "sesion_web_it_" + System.nanoTime();
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
        // Como en producción: un origen concreto y el dominio común.
        registry.add("application.security.cors.allowed-origins", () -> ORIGEN_WEB);
        registry.add("application.security.web-session.cookie-domain", () -> "nxtime-web.com");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CodigosEnviados codigos;

    private final ObjectMapper json = new ObjectMapper();

    private String email;

    /**
     * Una IP distinta por test para el login y el alta, que están limitados a
     * 10 por minuto y por IP (LoginRateLimitFilter): con una sola, a mitad de
     * la clase empezarían los 429. Es lo mismo que hace ApiContractTest.
     */
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private String ip;

    /** Una empresa nueva por test: el refresh de uno no puede afectar a otro. */
    @BeforeEach
    void registrarEmpresa() {
        ip = "198.51.100." + SIGUIENTE_IP.getAndIncrement();
        email = "ana." + System.nanoTime() + "@nxtime.test";
        String cuerpo = """
                {"nombreEmpresa":"Empresa %s","nombre":"Ana","apellidos":"Web","email":"%s","contrasena":"%s"}
                """.formatted(System.nanoTime(), email, CONTRASENA);
        ResponseEntity<String> alta = rest.exchange("/auth/register-manager", HttpMethod.POST,
                new HttpEntity<>(cuerpo, conIp(jsonHeaders())), String.class);
        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        // Desde la V37 hay que confirmar el correo antes de poder entrar.
        assertThat(confirmar(email, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> confirmar(String correo, String origen) {
        String cuerpo = origen == null
                ? "{\"email\":\"%s\",\"codigo\":\"%s\"}".formatted(correo, codigos.ultimoPara(correo))
                : "{\"email\":\"%s\",\"codigo\":\"%s\",\"origen\":\"%s\"}"
                        .formatted(correo, codigos.ultimoPara(correo), origen);
        return rest.exchange("/auth/registro/confirmar", HttpMethod.POST,
                new HttpEntity<>(cuerpo, conIp(jsonHeaders())), String.class);
    }

    /* ---------------------------------------------------------------- */
    /* Ayudas                                                            */
    /* ---------------------------------------------------------------- */

    private static HttpHeaders jsonHeaders() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return cabeceras;
    }

    private HttpHeaders conIp(HttpHeaders cabeceras) {
        cabeceras.set("X-Forwarded-For", ip);
        return cabeceras;
    }

    private ResponseEntity<String> login(String origen) {
        String cuerpo = origen == null
                ? "{\"email\":\"%s\",\"contrasena\":\"%s\"}".formatted(email, CONTRASENA)
                : "{\"email\":\"%s\",\"contrasena\":\"%s\",\"origen\":\"%s\"}".formatted(email, CONTRASENA, origen);
        return rest.exchange("/auth/login", HttpMethod.POST, new HttpEntity<>(cuerpo, conIp(jsonHeaders())), String.class);
    }

    private static List<String> setCookies(ResponseEntity<?> respuesta) {
        return Optional.ofNullable(respuesta.getHeaders().get(HttpHeaders.SET_COOKIE)).orElse(List.of());
    }

    private static Optional<String> setCookie(ResponseEntity<?> respuesta, String nombre) {
        return setCookies(respuesta).stream().filter(c -> c.startsWith(nombre + "=")).findFirst();
    }

    /** El valor de una cookie a partir de su {@code Set-Cookie}. */
    private static String valor(ResponseEntity<?> respuesta, String nombre) {
        String cabecera = setCookie(respuesta, nombre).orElseThrow();
        return cabecera.substring(nombre.length() + 1, cabecera.indexOf(';'));
    }

    /** Lo que mandaría el navegador desde la web: sus dos cookies, y las cabeceras que se le pidan. */
    private ResponseEntity<String> conCookies(String ruta, String refresh, String csrf, String cabeceraCsrf,
                                              String origen) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.add(HttpHeaders.COOKIE, "nx_refresh=" + refresh + "; nx_csrf=" + csrf);
        if (cabeceraCsrf != null) {
            cabeceras.set("X-CSRF-Token", cabeceraCsrf);
        }
        if (origen != null) {
            cabeceras.set(HttpHeaders.ORIGIN, origen);
        }
        return rest.exchange(ruta, HttpMethod.POST, new HttpEntity<>(cabeceras), String.class);
    }

    private JsonNode cuerpo(ResponseEntity<String> respuesta) throws Exception {
        return json.readTree(respuesta.getBody());
    }

    /* ---------------------------------------------------------------- */

    @Nested
    @DisplayName("El login desde la web")
    class Login {

        @Test
        @DisplayName("pone el refresh en una cookie HttpOnly y NO en el cuerpo")
        void refreshEnCookieYNoEnElCuerpo() throws Exception {
            ResponseEntity<String> respuesta = login("WEB");

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
            // En el cuerpo, el JavaScript de la página lo leería: justo lo que se quiere evitar.
            assertThat(cuerpo(respuesta).path("refreshToken").isNull()).isTrue();
            assertThat(cuerpo(respuesta).path("token").asText()).isNotBlank();

            String refresh = setCookie(respuesta, "nx_refresh").orElseThrow();
            assertThat(refresh).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/auth");
            assertThat(refresh).doesNotContain("Domain=");
        }

        @Test
        @DisplayName("pone una cookie CSRF legible y del dominio común, para que la web la vea")
        void cookieCsrfLegibleEnElDominioComun() {
            String csrf = setCookie(login("WEB"), "nx_csrf").orElseThrow();

            assertThat(csrf).contains("Secure", "SameSite=Strict", "Domain=nxtime-web.com", "Path=/");
            assertThat(csrf).doesNotContain("HttpOnly");
        }

        @Test
        @DisplayName("registrar una empresa desde la web: sin sesión hasta confirmar, y al confirmar, cookie y nada en el cuerpo")
        void registroDesdeLaWeb() throws Exception {
            String eva = "eva." + System.nanoTime() + "@nxtime.test";
            String cuerpo = """
                    {"nombreEmpresa":"Web %s","nombre":"Eva","apellidos":"Web","email":"%s",
                     "contrasena":"%s","origen":"WEB"}
                    """.formatted(System.nanoTime(), eva, CONTRASENA);
            ResponseEntity<String> registro = rest.exchange("/auth/register-manager", HttpMethod.POST,
                    new HttpEntity<>(cuerpo, conIp(jsonHeaders())), String.class);
            assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(setCookies(registro)).isEmpty();
            assertThat(cuerpo(registro).path("token").isMissingNode()).isTrue();

            ResponseEntity<String> alta = confirmar(eva, "WEB");

            assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cuerpo(alta).path("token").asText()).isNotBlank();
            assertThat(cuerpo(alta).path("refreshToken").isNull()).isTrue();
            assertThat(setCookie(alta, "nx_refresh").orElseThrow()).contains("HttpOnly");
            // 12 horas, las del navegador (ADR 019), y no los 30 días de la app.
            assertThat(setCookie(alta, "nx_refresh").orElseThrow()).contains("Max-Age=43200");
        }

        @Test
        @DisplayName("desde la app, como siempre: refresh en el cuerpo y ninguna cookie")
        void laAppNoCambia() throws Exception {
            ResponseEntity<String> respuesta = login(null);

            assertThat(cuerpo(respuesta).path("refreshToken").asText()).isNotBlank();
            assertThat(setCookies(respuesta)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Renovar con la cookie")
    class Renovar {

        @Test
        @DisplayName("con el CSRF correcto renueva, rota la cookie y no pone el refresh en el cuerpo")
        void conCsrf_renuevaYRota() throws Exception {
            ResponseEntity<String> entrada = login("WEB");
            String refresh = valor(entrada, "nx_refresh");
            String csrf = valor(entrada, "nx_csrf");

            ResponseEntity<String> renovada = conCookies("/auth/refresh", refresh, csrf, csrf, ORIGEN_WEB);

            assertThat(renovada.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cuerpo(renovada).path("token").asText()).isNotBlank();
            assertThat(cuerpo(renovada).path("refreshToken").isNull()).isTrue();
            assertThat(valor(renovada, "nx_refresh")).isNotEqualTo(refresh);
            assertThat(valor(renovada, "nx_csrf")).isNotEqualTo(csrf);
        }

        /* La rotación del ADR 019 sigue valiendo con la cookie: reutilizarla revoca la sesión. */
        @Test
        @DisplayName("reutilizar la cookie ya rotada se trata como un robo")
        void reutilizarLaCookie_revoca() {
            ResponseEntity<String> entrada = login("WEB");
            String refresh = valor(entrada, "nx_refresh");
            String csrf = valor(entrada, "nx_csrf");
            ResponseEntity<String> renovada = conCookies("/auth/refresh", refresh, csrf, csrf, ORIGEN_WEB);

            ResponseEntity<String> reutilizada = conCookies("/auth/refresh", refresh, csrf, csrf, ORIGEN_WEB);
            assertThat(reutilizada.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // Y la buena también ha caído: se revocó la familia entera.
            String nueva = valor(renovada, "nx_refresh");
            String nuevoCsrf = valor(renovada, "nx_csrf");
            assertThat(conCookies("/auth/refresh", nueva, nuevoCsrf, nuevoCsrf, ORIGEN_WEB).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("sin la cabecera CSRF, 403, y el refresh sigue vivo")
        void sinCabecera_403() {
            ResponseEntity<String> entrada = login("WEB");
            String refresh = valor(entrada, "nx_refresh");
            String csrf = valor(entrada, "nx_csrf");

            assertThat(conCookies("/auth/refresh", refresh, csrf, null, ORIGEN_WEB).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);

            // No se tocó: una petición de otra web no llega ni a rotarlo.
            assertThat(conCookies("/auth/refresh", refresh, csrf, csrf, ORIGEN_WEB).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("con una cabecera CSRF que no es la de la cookie, 403")
        void cabeceraDistinta_403() {
            ResponseEntity<String> entrada = login("WEB");

            ResponseEntity<String> respuesta = conCookies("/auth/refresh", valor(entrada, "nx_refresh"),
                    valor(entrada, "nx_csrf"), "otro-valor", ORIGEN_WEB);

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("desde otro origen, aunque el CSRF cuadre, 403")
        void otroOrigen_403() {
            ResponseEntity<String> entrada = login("WEB");
            String csrf = valor(entrada, "nx_csrf");

            ResponseEntity<String> respuesta = conCookies("/auth/refresh", valor(entrada, "nx_refresh"),
                    csrf, csrf, ORIGEN_AJENO);

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("sin cuerpo y sin cookie, 400, como cuando faltaba el refresh")
        void sinNada_400() {
            ResponseEntity<String> respuesta = rest.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(jsonHeaders()), String.class);

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("la app renueva con el refresh en el cuerpo, sin cookies ni CSRF")
        void laAppRenuevaComoSiempre() throws Exception {
            String refresh = cuerpo(login(null)).path("refreshToken").asText();

            ResponseEntity<String> renovada = rest.exchange("/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>("{\"refreshToken\":\"" + refresh + "\"}", jsonHeaders()), String.class);

            assertThat(renovada.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cuerpo(renovada).path("refreshToken").asText()).isNotBlank().isNotEqualTo(refresh);
            assertThat(setCookies(renovada)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cerrar sesión con la cookie")
    class Salir {

        @Test
        @DisplayName("revoca el refresh y borra las dos cookies")
        void revocaYBorra() {
            ResponseEntity<String> entrada = login("WEB");
            String refresh = valor(entrada, "nx_refresh");
            String csrf = valor(entrada, "nx_csrf");

            ResponseEntity<String> salida = conCookies("/auth/logout", refresh, csrf, csrf, ORIGEN_WEB);

            assertThat(salida.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(setCookie(salida, "nx_refresh").orElseThrow()).contains("Max-Age=0");
            assertThat(setCookie(salida, "nx_csrf").orElseThrow()).contains("Max-Age=0");
            assertThat(conCookies("/auth/refresh", refresh, csrf, csrf, ORIGEN_WEB).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        /* Sin CSRF, otra web podría cerrar la sesión de quien la visita. */
        @Test
        @DisplayName("sin CSRF, 403, y la sesión sigue abierta")
        void sinCsrf_403() {
            ResponseEntity<String> entrada = login("WEB");
            String refresh = valor(entrada, "nx_refresh");
            String csrf = valor(entrada, "nx_csrf");

            assertThat(conCookies("/auth/logout", refresh, csrf, null, ORIGEN_WEB).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(conCookies("/auth/refresh", refresh, csrf, csrf, ORIGEN_WEB).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @DisplayName("CORS admite credenciales y la cabecera CSRF desde la web, y no desde otro origen")
    void corsConCredenciales() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set(HttpHeaders.ORIGIN, ORIGEN_WEB);
        cabeceras.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        cabeceras.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-CSRF-Token");
        ResponseEntity<String> preflight = rest.exchange("/auth/refresh", HttpMethod.OPTIONS,
                new HttpEntity<>(cabeceras), String.class);

        assertThat(preflight.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(preflight.getHeaders().getAccessControlAllowCredentials()).isTrue();
        assertThat(preflight.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ORIGEN_WEB);
        assertThat(preflight.getHeaders().getAccessControlAllowHeaders()).contains("X-CSRF-Token");

        cabeceras.set(HttpHeaders.ORIGIN, ORIGEN_AJENO);
        assertThat(rest.exchange("/auth/refresh", HttpMethod.OPTIONS, new HttpEntity<>(cabeceras), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
