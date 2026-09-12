package com.nxtime.nxtime.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import org.springframework.http.converter.ByteArrayHttpMessageConverter;

import com.nxtime.nxtime.notification.EmailSender;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests de CONTRATO (Fase 0 del plan de profesionalización).
 *
 * Objetivo: fijar en un test ejecutable la forma real de los endpoints
 * HTTP que consume la app Android. Ha ido evolucionando junto con el
 * backend en cada fase:
 *
 *   - Fase 1 (migración Kotlin -> Java): el criterio de aceptación fue
 *     que este fichero pasara SIN MODIFICARLO -- de ahí que use JSON
 *     crudo (Map/JsonNode) en vez de las clases del proyecto, y no
 *     dependiera de nada que la migración fuese a borrar.
 *
 *   - Fase 2 (manejo de errores, DTOs...): el contrato cambió a
 *     propósito. Los tests que documentaban un "BUG ACTUAL" (ver el
 *     historial de commits) se actualizaron para esperar el
 *     comportamiento correcto.
 *
 *   - Fase 3 (PostgreSQL): la base de datos de test pasa de un fichero
 *     SQLite desechable a una base de datos PostgreSQL real, creada de
 *     cero en cada ejecución (ver freshTestDatabase abajo) sobre el
 *     Postgres de docker-compose.yml. Flyway aplica el esquema real
 *     (V1__initial_schema.sql) al arrancar el contexto. Los campos de
 *     instante (horaEntrada/horaSalida) pasan de LocalDateTime a
 *     Instant: en JSON llevan sufijo "Z" (UTC).
 *
 *     Se intentó primero con Testcontainers (un PostgreSQL en su propio
 *     contenedor Docker, autogestionado): en este equipo, con Docker
 *     Desktop 4.87 en Windows, ninguno de los tres transportes
 *     disponibles (pipes con nombre ni el daemon expuesto por TCP)
 *     funciona con la librería docker-java que usa Testcontainers
 *     1.21.3 -- verificado con un cliente docker-java aislado, no es un
 *     problema de configuración. Es plausible que sea específico de
 *     esta combinación concreta y no se reproduzca en Linux (CI de la
 *     Fase 11). Mientras tanto, requiere tener
 *     `docker compose up -d postgres` corriendo antes de lanzar los tests.
 *
 *   - Fase 4 (seguridad reforzada): registerManager pasa a crear un
 *     ADMIN (no un GESTOR) -- quien funda el tenant lo administra, ver
 *     RoleAuthorities. Login y registro devuelven además un
 *     refreshToken (access token corto, 15 min). Los 401 ahora sí son
 *     401 con ProblemDetail (antes 403, o directamente un error no
 *     controlado -- ver RestAuthenticationEntryPoint,
 *     RestAccessDeniedHandler y el try/catch de JwtAuthenticationFilter).
 *
 * Requisito para ejecutar esta clase: `docker compose up -d postgres`
 * (ver docker-compose.yml en la raíz del monorepo) con el puerto 5433.
 *
 * Los tests están ordenados porque construyen un flujo de negocio
 * encadenado (registrar empresa -> crear empleado -> fichar -> pedir
 * ausencia -> aprobarla...), igual que lo haría la app real. Se
 * ejecuta una única instancia de la clase (PER_CLASS) contra una única
 * base de datos, creada una vez para toda la clase.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiContractTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5433/nxtime";
    private static final String DB_USER = "nxtime";
    private static final String DB_PASSWORD = "nxtime";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "contract_test_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection(ADMIN_URL, DB_USER, DB_PASSWORD);
             Statement statement = admin.createStatement()) {
            // CREATE DATABASE no puede ir dentro de una transacción; la
            // conexión JDBC por defecto va en autocommit, así que esto
            // se ejecuta y confirma de inmediato.
            statement.execute("CREATE DATABASE " + testDb);
        }

        String testUrl = "jdbc:postgresql://localhost:5433/" + testDb;
        // Fase 8: Flyway migra con el rol admin (DB_USER/DB_PASSWORD,
        // "nxtime" -- necesita DDL); la app en runtime se conecta como
        // "nxtime_app", sin privilegios de superusuario -- ver
        // application-dev.yml y docker/postgres/init-app-role.sql.
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> "nxtime_app");
        registry.add("spring.datasource.password", () -> "nxtime_app");
        registry.add("spring.flyway.url", () -> testUrl);
        registry.add("spring.flyway.user", () -> DB_USER);
        registry.add("spring.flyway.password", () -> DB_PASSWORD);
    }

    @Value("${local.server.port}")
    private int port;

    private final TestRestTemplate rest = restConDescargas();

    /**
     * Un TestRestTemplate que sabe leer respuestas binarias.
     *
     * Por defecto no puede extraer un {@code byte[]} de un
     * {@code application/pdf}, y desde la Fase B2 hay endpoints que
     * devuelven exactamente eso. Se añade un ByteArrayHttpMessageConverter
     * que acepta cualquier tipo de contenido.
     */
    private static TestRestTemplate restConDescargas() {
        TestRestTemplate plantilla = new TestRestTemplate();
        ByteArrayHttpMessageConverter binario = new ByteArrayHttpMessageConverter();
        binario.setSupportedMediaTypes(List.of(MediaType.ALL));
        plantilla.getRestTemplate().getMessageConverters().add(0, binario);
        return plantilla;
    }
    private final ObjectMapper json = new ObjectMapper();

    // Estado compartido entre tests ordenados (construyen un flujo real).
    private static final String EMPRESA = "Contract Test SL";
    private static final String EMAIL_GESTOR = "gestor.contract@nxtime.test";
    private static final String EMAIL_EMPLEADO = "empleado.contract@nxtime.test";
    private static final String EMAIL_GESTOR2 = "gestor2.contract@nxtime.test";
    private static final String EMPRESA_OTRA = "Otra Empresa Contract SL";
    private static final String EMAIL_GESTOR_OTRA_EMPRESA = "gestor.otraempresa@nxtime.test";
    private String gestorToken;
    private String gestor2Token;
    private String empleadoToken;
    private String empleadoRefreshToken;
    private String gestorOtraEmpresaToken;
    private long empleadoId;
    private long registroActivoId;
    private long peticionAusenciaId;
    private long departamentoId;
    private long correccionId;
    private long adjuntoId;
    private long festivoId;
    private long festivoNacionalId;
    private long proyectoId;
    private long otroProyectoId;
    private long asignacionId;
    // Fase G. El código se guarda aquí porque el servidor NO lo puede
    // volver a dar: es la única copia que existe fuera del hash, igual
    // que le pasa a quien denuncia de verdad.
    private String codigoDenunciaAnonima;
    private long denunciaAnonimaId;
    private long ofertaId;
    private long candidaturaId;
    // Fase H. El adjunto que la candidatura congela: el test 149 es
    // exactamente comprobar que sigue existiendo cuando su dueño sube
    // otro CV encima.
    private long cvCongeladoId;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String toJson(Map<String, Object> body) throws Exception {
        return json.writeValueAsString(body);
    }

    private JsonNode bodyOf(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody());
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    // ------------------------------------------------------------------
    // 1. AUTENTICACIÓN
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void registrarGestor_datosValidos_devuelve200ConTokenNombreYRol() throws Exception {
        Map<String, Object> peticion = mapOf(
                "nombreEmpresa", EMPRESA,
                "nombre", "Gestor",
                "apellidos", "Contract",
                "email", EMAIL_GESTOR,
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/register-manager"),
                new HttpEntity<>(toJson(peticion), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("token").asText()).isNotBlank();
        // CORREGIDO EN FASE 4: quien registra la empresa es ADMIN, no
        // GESTOR -- es quien administra el tenant, y es el único rol con
        // "gestor:crear" (ver RoleAuthorities). Antes cualquier GESTOR
        // podía crear otro GESTOR sin límite (ver auditoría).
        // Desde el 09/2026 el registro pide nombre y apellidos por separado,
        // asi que lo que viaja aqui es el NOMBRE -- que es con lo que la app
        // saluda ("Hola, Gestor"), no la ficha completa.
        assertThat(body.get("nombre").asText()).isEqualTo("Gestor");
        assertThat(body.get("rol").asText()).isEqualTo("ADMIN");
        // NUEVO EN FASE 4: refresh token de larga duración, para pedir
        // un access token nuevo sin volver a pedir contraseña.
        assertThat(body.get("refreshToken").asText()).isNotBlank();

        gestorToken = body.get("token").asText();
    }

    @Test
    @Order(2)
    void registrarGestor_empresaDuplicada_devuelve409ConProblemDetail() throws Exception {
        // CORREGIDO EN FASE 2: hasta ahora este caso devolvía 403 en vez
        // de 409 (ver commit de la Fase 0): ResponseStatusException usa
        // response.sendError(), que dispara un dispatch interno a
        // "/error", y ese dispatch volvía a pasar por el filtro de
        // seguridad y caía en el denyAll(). GlobalExceptionHandler ya no
        // usa sendError() -- construye la respuesta directamente dentro
        // del propio ciclo de DispatcherServlet -- así que el 409 llega
        // tal cual, con un cuerpo ProblemDetail real.
        Map<String, Object> peticion = mapOf(
                "nombreEmpresa", EMPRESA, // misma empresa que en el test anterior
                "nombre", "Otro",
                "apellidos", "Gestor",
                "email", "otro.gestor@nxtime.test",
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/register-manager"),
                new HttpEntity<>(toJson(peticion), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = bodyOf(response);
        assertThat(body.get("status").asInt()).isEqualTo(409);
        assertThat(body.get("detail").asText()).contains("La empresa ya existe");
    }

    @Test
    @Order(3)
    void login_credencialesDeGestorValidas_devuelve200ConToken() throws Exception {
        Map<String, Object> peticion = mapOf(
                "email", EMAIL_GESTOR,
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(peticion), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("rol").asText()).isEqualTo("ADMIN");

        gestorToken = body.get("token").asText();
    }

    @Test
    @Order(4)
    void login_contrasenaIncorrecta_devuelve401ConProblemDetail() throws Exception {
        // GlobalExceptionHandler mapea AuthenticationException (la que
        // lanza el AuthenticationManager para una contraseña incorrecta)
        // a 401 desde la Fase 2 -- este test se quedó sin actualizar en
        // su momento, documentando el "bug" con una aserción laxa
        // (4xx-o-5xx). Se corrige aquí, en la Fase 4, de paso.
        Map<String, Object> peticion = Map.of(
                "email", EMAIL_GESTOR,
                "contrasena", "contrasena-incorrecta"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(peticion, jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(response).get("status").asInt()).isEqualTo(401);
    }

    @Test
    @Order(5)
    void endpointProtegido_sinToken_devuelve401ConProblemDetail() throws Exception {
        // CORREGIDO EN FASE 4: antes Spring Security devolvía 403 (con
        // el HTML de error de Tomcat) para una petición no autenticada
        // contra una ruta protegida, al no haber un
        // AuthenticationEntryPoint personalizado. Ahora RestAuthenticationEntryPoint
        // responde 401 con ProblemDetail -- "no sé quién eres", que es
        // semánticamente distinto de 403 ("sí sé quién eres, pero no
        // puedes").
        ResponseEntity<String> response = rest.getForEntity(
                url("/api/v1/fichaje/activo"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(response).get("status").asInt()).isEqualTo(401);
    }

    @Test
    @Order(6)
    void registrarGestor_datosInvalidos_devuelve400ConValidacion() throws Exception {
        // NUEVO EN FASE 2: Bean Validation en los DTOs de entrada (ver
        // plan, defecto #6 "Cero validación de entrada"). Antes de esta
        // fase, un email vacío y una contraseña vacía se aceptaban sin
        // más -- passwordEncoder.encode("") funcionaba y persistía el
        // usuario tal cual.
        Map<String, Object> peticion = mapOf(
                "nombreEmpresa", "",
                "nombre", "",
                "apellidos", "",
                "email", "esto-no-es-un-email",
                "contrasena", "123" // menos de 8 caracteres
        );

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/register-manager"),
                new HttpEntity<>(toJson(peticion), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(7)
    void registrarSegundaEmpresa_creaTenantIndependiente() throws Exception {
        // Prepara el test de aislamiento multi-tenant (sección 4): un
        // tenant completamente aparte, con su propio gestor, que no
        // debe poder ver ni tocar nada de EMPRESA.
        Map<String, Object> peticion = mapOf(
                "nombreEmpresa", EMPRESA_OTRA,
                "nombre", "Gestor",
                "apellidos", "Otra Empresa",
                "email", EMAIL_GESTOR_OTRA_EMPRESA,
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/register-manager"),
                new HttpEntity<>(toJson(peticion), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        gestorOtraEmpresaToken = bodyOf(response).get("token").asText();
    }

    // ------------------------------------------------------------------
    // 2. GESTIÓN DE EMPLEADOS (rol GESTOR)
    // ------------------------------------------------------------------

    /*
     * Desde el 09/2026 (ADR 014) un alta no lleva contraseña: la persona la
     * elige con el código que le llega por correo. El correo es un mock y el
     * código se lee de lo que se le pasó.
     *
     * Ojo: @MockitoBean se reinicia después de CADA test, así que el código
     * se lee y se usa dentro del mismo test, nunca en el siguiente.
     */
    @MockitoBean
    private EmailSender emailSender;

    private String codigoEnviadoA(String email) {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.captor();
        verify(emailSender).enviarObligatorio(eq(email), anyString(), anyString(), variables.capture());
        return (String) variables.getValue().get("codigo");
    }

    /**
     * Elegir contraseña con un código. Desde una IP falsa propia, como el
     * login del Order 28: /auth/recuperar/confirmar comparte el límite por IP
     * con /auth/login, y no debe gastar el cupo del resto del flujo.
     */
    private ResponseEntity<String> elegirContrasena(String email, String codigo, String contrasena, String ip)
            throws Exception {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Forwarded-For", ip);
        return rest.postForEntity(
                url("/auth/recuperar/confirmar"),
                new HttpEntity<>(toJson(mapOf("email", email, "codigo", codigo, "contrasenaNueva", contrasena)), headers),
                String.class
        );
    }

    @Test
    @Order(10)
    void gestorCreaEmpleadoSinContrasena_yElEmpleadoLaEligeConElCodigo() throws Exception {
        Map<String, Object> peticion = mapOf(
                "nombre", "Empleado",
                "apellidos", "Contract",
                "email", EMAIL_EMPLEADO
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/empleados"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(gestorToken)),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> eleccion =
                elegirContrasena(EMAIL_EMPLEADO, codigoEnviadoA(EMAIL_EMPLEADO), "password123", "203.0.113.60");
        assertThat(eleccion.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(11)
    void gestorCreaOtroGestorSinContrasena_yLaEligeConElCodigo() throws Exception {
        Map<String, Object> peticion = mapOf(
                "nombre", "Gestor",
                "apellidos", "Contract 2",
                "email", EMAIL_GESTOR2
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/gestores"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(gestorToken)),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> eleccion =
                elegirContrasena(EMAIL_GESTOR2, codigoEnviadoA(EMAIL_GESTOR2), "password123", "203.0.113.61");
        assertThat(eleccion.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(12)
    void loginEmpleado_devuelve200ConRolEmpleado() throws Exception {
        Map<String, Object> peticion = mapOf(
                "email", EMAIL_EMPLEADO,
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(peticion), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("rol").asText()).isEqualTo("EMPLEADO");

        empleadoToken = body.get("token").asText();
    }

    @Test
    @Order(13)
    void gestorListaSusEmpleados_incluyeAlRecienCreado() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/mis-empleados"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.isArray()).isTrue();

        JsonNode empleado = null;
        for (JsonNode n : body) {
            if (EMAIL_EMPLEADO.equals(n.get("email").asText())) {
                empleado = n;
            }
        }
        assertThat(empleado).as("el empleado recién creado debe aparecer en la lista").isNotNull();
        empleadoId = empleado.get("id").asLong();
    }

    @Test
    @Order(14)
    void empleadoNoPuedeAccederAEndpointDeGestor_devuelve403() {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/mis-empleados"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // 3. FICHAJE
    // ------------------------------------------------------------------

    @Test
    @Order(20)
    void ficharInicio_devuelve200_yYaNoFiltraElHashDeLaContrasena() throws Exception {
        Map<String, Object> peticion = mapOf("tipo", "INICIO");

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("horaEntrada").asText()).isNotBlank();
        assertThat(body.get("enPausa").asBoolean()).isFalse();
        assertThat(body.get("minutosPausaAcumulados").asLong()).isZero();
        registroActivoId = body.get("id").asLong();

        // CORREGIDO EN FASE 2 (ver plan, defecto #1 "Fuga del hash BCrypt
        // por la API"): el controlador ya no devuelve la entidad JPA
        // completa -- mapea a TimeEntryResponse, que ni siquiera tiene un
        // campo "usuario". El hash de la contraseña ya no puede viajar
        // por aquí.
        assertThat(body.has("usuario")).as("ya no debe viajar la entidad Usuario anidada").isFalse();
        assertThat(body.has("contrasena")).isFalse();
    }

    @Test
    @Order(21)
    void ficharActivo_devuelveElRegistroAbierto() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/activo"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("id").asLong()).isEqualTo(registroActivoId);
    }

    @Test
    @Order(22)
    void ficharInicioDosVeces_devuelve409ConProblemDetail() throws Exception {
        // CORREGIDO EN FASE 2: el IllegalStateException de "Ya hay una
        // jornada activa" del Kotlin original se sustituyó por
        // BusinessException, que GlobalExceptionHandler resuelve a 409
        // directamente (sin pasar por response.sendError()). Antes de
        // este cambio devolvía 403 (ver historial de commits, Fase 0).
        Map<String, Object> peticion = mapOf("tipo", "INICIO");

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("detail").asText()).contains("Ya hay una jornada activa");
    }

    @Test
    @Order(23)
    void ficharPausaInicioYFin_devuelven200() throws Exception {
        ResponseEntity<String> pausaInicio = rest.exchange(
                url("/api/v1/fichaje"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("tipo", "PAUSA_INICIO")), authHeaders(empleadoToken)),
                String.class
        );
        assertThat(pausaInicio.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(pausaInicio).get("enPausa").asBoolean()).isTrue();

        ResponseEntity<String> pausaFin = rest.exchange(
                url("/api/v1/fichaje"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("tipo", "PAUSA_FIN")), authHeaders(empleadoToken)),
                String.class
        );
        assertThat(pausaFin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(pausaFin).get("enPausa").asBoolean()).isFalse();
    }

    @Test
    @Order(24)
    void ficharFin_cierraLaJornada_devuelve200() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("tipo", "FIN")), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("horaSalida").isNull()).isFalse();
    }

    @Test
    @Order(25)
    void ficharActivo_sinJornadaAbierta_devuelve204() {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/activo"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(26)
    void historialDelEmpleado_incluyeElFichajeCerrado() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/historial"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(27)
    void historialDelEquipo_paraElGestor_devuelveFechasIsoTipadas() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/gestor/historial"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThanOrEqualTo(1);

        JsonNode primero = body.get(0);
        // CORREGIDO EN FASE 2: antes horaEntrada/fecha viajaban como
        // String preformateado ("HH:mm:ss" / "yyyy-MM-dd"), no ISO-8601
        // tipado (ver plan, defectos de diseño). Desde la Fase 3,
        // horaEntrada es un Instant real (sufijo "Z" = UTC explícito),
        // no un LocalDateTime "ingenuo".
        assertThat(primero.get("horaEntrada").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        assertThat(primero.get("fecha").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(primero.get("usuario").get("nombre").asText()).isNotBlank();
        assertThat(primero.get("minutosPausaAcumulados").asLong()).isGreaterThanOrEqualTo(0);
    }

    // ------------------------------------------------------------------
    // 3b. AUDITORÍA Y CORRECCIÓN DE FICHAJES (Fase 8)
    // ------------------------------------------------------------------

    @Test
    @Order(28)
    void gestorSinRolRRHH_noPuedeCorregirFichaje_devuelve403() throws Exception {
        // X-Forwarded-For falsa (ver login_conDemasiadosIntentos_devuelve429,
        // Order 53): un /auth/login más no debe consumir el cupo de la
        // IP real, ya ajustado para el resto del flujo de esta clase.
        HttpHeaders loginHeaders = jsonHeaders();
        loginHeaders.set("X-Forwarded-For", "203.0.113.56");
        ResponseEntity<String> login = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(mapOf("email", EMAIL_GESTOR2, "contrasena", "password123")), loginHeaders),
                String.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        gestor2Token = bodyOf(login).get("token").asText();

        Map<String, Object> correccion = mapOf(
                "horaEntrada", "2026-01-01T08:00:00Z",
                "horaSalida", "2026-01-01T17:00:00Z",
                "motivo", "Un GESTOR normal no deberia poder pedir esto sobre el fichaje de otro."
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/" + registroActivoId + "/correcciones"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(correccion), authHeaders(gestor2Token)),
                String.class
        );

        // Desde la Fase E cualquiera puede PEDIR una correccion de su
        // propio fichaje, pero pedirla sobre el de OTRA persona sigue
        // exigiendo "fichaje:corregir" (RRHH+), que un GESTOR no tiene.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(29)
    void adminPideCorreccionDeFichajeAjeno_quedaPendienteDeQueLaAcepteElEmpleado() throws Exception {
        Map<String, Object> correccion = mapOf(
                "horaEntrada", "2026-01-01T08:00:00Z",
                "horaSalida", "2026-01-01T17:00:00Z",
                "motivo", "El empleado ficho la entrada con 15 minutos de retraso por error del reloj."
        );

        // gestorToken es en realidad ADMIN (ver Fase 4). Antes esto
        // corregia EN EL ACTO y devolvia 200; ahora crea una SOLICITUD y
        // devuelve 202, porque el fichaje no se ha tocado: quien decide
        // es el empleado, que es a quien le cambian sus horas.
        ResponseEntity<String> solicitud = rest.exchange(
                url("/api/v1/fichaje/" + registroActivoId + "/correcciones"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(correccion), authHeaders(gestorToken)),
                String.class
        );

        assertThat(solicitud.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode cuerpo = bodyOf(solicitud);
        assertThat(cuerpo.get("estado").asText()).isEqualTo("PENDIENTE");
        correccionId = cuerpo.get("id").asLong();

        // El fichaje sigue intacto y en el historial del empleado.
        ResponseEntity<String> historial = rest.exchange(
                url("/api/v1/fichaje/historial"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        boolean sigueElOriginal = false;
        for (JsonNode fichaje : bodyOf(historial)) {
            if (fichaje.get("id").asLong() == registroActivoId) {
                sigueElOriginal = true;
            }
        }
        assertThat(sigueElOriginal)
                .as("mientras la correccion esta pendiente, el fichaje no cambia")
                .isTrue();

        // Y al empleado le aparece como algo que tiene que resolver EL,
        // aunque no tenga ninguna authority de aprobacion.
        ResponseEntity<String> pendientes = rest.exchange(
                url("/api/v1/correcciones/pendientes"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(pendientes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(pendientes)).hasSize(1);
        assertThat(bodyOf(pendientes).get(0).get("puedoResolver").asBoolean()).isTrue();
        assertThat(bodyOf(pendientes).get(0).get("puedoDisputar").asBoolean()).isTrue();
    }

    @Test
    @Order(292)
    void quienPidioLaCorreccionNoPuedeAprobarsela_devuelve403() throws Exception {
        // El caso que da sentido a toda la fase: si quien la pide pudiera
        // aprobarsela, seguiria cambiando las horas de otro por su cuenta.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/correcciones/" + correccionId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("aprobada", true)), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(293)
    void elEmpleadoApruebaLaCorreccionDeSuFichaje_yEntoncesSiSeAplica() throws Exception {
        ResponseEntity<String> resuelta = rest.exchange(
                url("/api/v1/correcciones/" + correccionId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(
                        toJson(mapOf("aprobada", true, "comentario", "Es verdad, el reloj iba mal")),
                        authHeaders(empleadoToken)),
                String.class
        );

        assertThat(resuelta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(resuelta).get("estado").asText()).isEqualTo("APROBADA");

        // Ahora si: el original queda anulado y desaparece del historial,
        // sustituido por la version corregida.
        ResponseEntity<String> historial = rest.exchange(
                url("/api/v1/fichaje/historial"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        boolean apareceElOriginal = false;
        for (JsonNode fichaje : bodyOf(historial)) {
            if (fichaje.get("id").asLong() == registroActivoId) {
                apareceElOriginal = true;
            }
        }
        assertThat(apareceElOriginal).as("el fichaje anulado no debe salir en el historial").isFalse();

        // La traza del original lo cuenta entero: lo que se pidio y lo
        // que acabo aplicandose.
        ResponseEntity<String> auditoria = rest.exchange(
                url("/api/v1/auditoria/fichaje/" + registroActivoId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(auditoria.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode trail = bodyOf(auditoria);
        assertThat(trail.get(0).get("accion").asText()).isEqualTo("CREACION");

        List<String> acciones = new ArrayList<>();
        for (JsonNode fila : trail) {
            acciones.add(fila.get("accion").asText());
        }
        // La solicitud queda anotada aunque por si sola no cambiara nada.
        assertThat(acciones).contains("SOLICITUD_CORRECCION", "CORRECCION");
        assertThat(trail.get(trail.size() - 1).get("accion").asText()).isEqualTo("CORRECCION");
    }

    @Test
    @Order(294)
    void pedirOtraCorreccionDelMismoOriginalYaAnulado_devuelve409() throws Exception {
        // Corregir el mismo original dos veces no tiene sentido: hay que
        // corregir la version nueva, no la ya sustituida.
        Map<String, Object> correccion = mapOf(
                "horaEntrada", "2026-01-01T08:00:00Z",
                "horaSalida", "2026-01-01T17:00:00Z",
                "motivo", "Segundo intento sobre el mismo original."
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/" + registroActivoId + "/correcciones"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(correccion), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("detail").asText()).contains("ya fue corregido");
    }

    // ------------------------------------------------------------------
    // 4. AUSENCIAS
    // ------------------------------------------------------------------

    @Test
    @Order(30)
    void empleadoSolicitaAusencia_devuelve200ConEstadoPendiente() throws Exception {
        Map<String, Object> peticion = mapOf(
                "fechaInicio", "2027-01-10",
                "fechaFin", "2027-01-12",
                "tipo", "VACACIONES",
                "motivo", "Test de contrato"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(body.get("tipo").asText()).isEqualTo("VACACIONES");
        assertThat(body.get("fechaInicio").asText()).isEqualTo("2027-01-10");

        peticionAusenciaId = body.get("id").asLong();
    }

    @Test
    @Order(31)
    void empleadoVeSusPropiasPeticiones() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias/mis-peticiones"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.size()).isEqualTo(1);
    }

    @Test
    @Order(32)
    void gestorVeLaPeticionPendienteDeSuEquipo() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias/gestor/pendientes"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        boolean encontrada = false;
        for (JsonNode n : body) {
            if (n.get("id").asLong() == peticionAusenciaId) {
                encontrada = true;
            }
        }
        assertThat(encontrada).isTrue();
    }

    @Test
    @Order(325)
    void gestorDeOtraEmpresaNoVeNiPuedeAprobarPeticionAjena_aislamientoMultiTenant() throws Exception {
        // Test de aislamiento multi-tenant (Fase 3, Paso 5 del plan): un
        // gestor de una empresa completamente distinta ni ve la
        // petición pendiente de EMPRESA en su lista de "pendientes", ni
        // puede aprobarla si intenta forzar el id directamente -- la
        // base de datos (empresa_id denormalizado) y TenantAccessException
        // se lo impiden, no solo el hecho de no tener el id a mano.
        ResponseEntity<String> pendientes = rest.exchange(
                url("/api/v1/ausencias/gestor/pendientes"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorOtraEmpresaToken)),
                String.class
        );
        assertThat(pendientes.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (JsonNode n : bodyOf(pendientes)) {
            assertThat(n.get("id").asLong()).isNotEqualTo(peticionAusenciaId);
        }

        // Fase 9: PATCH /{id}/estado sustituye a los dos POST
        // (/gestor/aprobar/{id} y /gestor/rechazar/{id}).
        ResponseEntity<String> aprobar = rest.exchange(
                url("/api/v1/ausencias/" + peticionAusenciaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "APROBADA")), authHeaders(gestorOtraEmpresaToken)),
                String.class
        );
        assertThat(aprobar.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(aprobar).get("detail").asText()).contains("otra empresa");
    }

    @Test
    @Order(33)
    void gestorApruebaLaPeticion_devuelve200ConEstadoAprobadaYTrazabilidad() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias/" + peticionAusenciaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(
                        toJson(mapOf("estado", "APROBADA", "comentario", "Aprobada, que las disfrutes.")),
                        authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("estado").asText()).isEqualTo("APROBADA");
        // Fase 9: ahora queda constancia de QUIÉN resolvió y CUÁNDO
        // (antes la petición solo cambiaba de estado, ver auditoría).
        assertThat(body.get("aprobadoPor").get("nombre").asText()).isNotBlank();
        assertThat(body.get("fechaResolucion").asText()).isNotBlank();
        assertThat(body.get("comentarioResolucion").asText()).contains("disfrutes");
        // Y los días hábiles reales, sin contar sábados ni domingos.
        assertThat(body.get("diasHabiles").asInt()).isPositive();
    }

    @Test
    @Order(34)
    void aprobarUnaPeticionYaResuelta_devuelve409ConProblemDetail() throws Exception {
        // CORREGIDO EN FASE 2: "Solo se puede modificar una petición
        // PENDIENTE." ahora es BusinessException -> 409 real.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias/" + peticionAusenciaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "APROBADA")), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("detail").asText()).contains("PENDIENTE");
    }

    @Test
    @Order(35)
    void gestorVeElHistorialDeAusenciasResueltas() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/ausencias-historial"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        boolean encontrada = false;
        for (JsonNode n : body) {
            if (n.get("id").asLong() == peticionAusenciaId && "APROBADA".equals(n.get("estado").asText())) {
                encontrada = true;
            }
        }
        assertThat(encontrada).isTrue();
    }

    @Test
    @Order(36)
    void unaSegundaSolicitudPuedeSerRechazada() throws Exception {
        Map<String, Object> nuevaPeticion = mapOf(
                "fechaInicio", "2027-02-01",
                "fechaFin", "2027-02-02",
                "tipo", "MEDICO",
                "motivo", null
        );
        ResponseEntity<String> creada = rest.exchange(
                url("/api/v1/ausencias"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(nuevaPeticion), authHeaders(empleadoToken)),
                String.class
        );
        long id = bodyOf(creada).get("id").asLong();

        // Fase 9: rechazar SIN comentario se rechaza con 400 -- negar
        // una ausencia sin explicar por qué no es aceptable.
        ResponseEntity<String> rechazoSinMotivo = rest.exchange(
                url("/api/v1/ausencias/" + id + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "RECHAZADA")), authHeaders(gestorToken)),
                String.class
        );
        assertThat(rechazoSinMotivo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(rechazoSinMotivo).get("detail").asText()).contains("motivo");

        ResponseEntity<String> rechazo = rest.exchange(
                url("/api/v1/ausencias/" + id + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(
                        toJson(mapOf("estado", "RECHAZADA", "comentario", "Coincide con el cierre trimestral.")),
                        authHeaders(gestorToken)),
                String.class
        );

        assertThat(rechazo.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(rechazo);
        assertThat(body.get("estado").asText()).isEqualTo("RECHAZADA");
        assertThat(body.get("comentarioResolucion").asText()).contains("cierre trimestral");
    }

    @Test
    @Order(37)
    void solicitarUnaAusenciaQueSeSolapaConOtraViva_devuelve409() throws Exception {
        // Fase 9: antes no se comprobaba el solapamiento en absoluto
        // (ver auditoría) -- se podían pedir dos veces las mismas fechas.
        // La petición del test 30 (2027-01-10 a 2027-01-12) sigue viva
        // (quedó APROBADA en el test 33); esta la pisa parcialmente.
        Map<String, Object> solapada = mapOf(
                "fechaInicio", "2027-01-12",
                "fechaFin", "2027-01-14",
                "tipo", "VACACIONES",
                "motivo", "Se solapa con la anterior"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(solapada), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("detail").asText()).contains("se solapa");
    }

    @Test
    @Order(38)
    void empleadoConsultaSuSaldoDeVacaciones() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias/saldo-vacaciones?anio=2027"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("anio").asInt()).isEqualTo(2027);
        assertThat(body.get("diasTotales").asInt()).isEqualTo(22); // derecho por defecto
        // La petición del test 30 quedó APROBADA: del domingo 10 al
        // martes 12 de enero de 2027 son 3 días naturales pero solo 2
        // HÁBILES (el domingo no cuenta) -- justo lo que esta fase
        // arregla: antes se habrían contado los 3.
        assertThat(body.get("diasConsumidos").asInt()).isEqualTo(2);
        assertThat(body.get("diasDisponibles").asInt())
                .isEqualTo(body.get("diasTotales").asInt() - body.get("diasConsumidos").asInt());
    }

    // ------------------------------------------------------------------
    // 5. PERFIL DE USUARIO
    // ------------------------------------------------------------------

    @Test
    @Order(40)
    void cambiarContrasena_conContrasenaAntiguaIncorrecta_devuelve400ConProblemDetail() throws Exception {
        // CORREGIDO EN FASE 2: el servicio lanza BusinessException con
        // status BAD_REQUEST explícito, y ahora sí llega tal cual (400),
        // en vez del 403 de antes.
        Map<String, Object> peticion = mapOf(
                "contrasenaAntigua", "no-es-la-contrasena",
                "contrasenaNueva", "nuevaPassword123"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/usuario/cambiar-contrasena"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).get("detail").asText()).contains("contraseña antigua");
    }

    @Test
    @Order(41)
    void cambiarContrasena_datosCorrectos_devuelve200_yPermiteLoginConLaNueva() throws Exception {
        Map<String, Object> peticion = mapOf(
                "contrasenaAntigua", "password123",
                "contrasenaNueva", "nuevaPassword123"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/usuario/cambiar-contrasena"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(empleadoToken)),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> login = mapOf(
                "email", EMAIL_EMPLEADO,
                "contrasena", "nuevaPassword123"
        );
        ResponseEntity<String> loginResponse = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(login), jsonHeaders()),
                String.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // 5.b FICHA DE EMPLEADO (Fase A)
    // ------------------------------------------------------------------
    // Va DESPUÉS del bloque de ausencias a propósito: el test 38
    // comprueba el saldo por defecto de 22 días, y fijarlo a mano antes
    // lo rompería. (Consulta el año 2027, así que en realidad no chocan,
    // pero el orden deja clara la intención.)

    @Test
    @Order(44)
    void rrhhConfiguraLaFichaDeUnEmpleado_devuelve200ConLosValoresNuevos() throws Exception {
        // El gestor de este flujo es en realidad un ADMIN
        // (registerManager crea ADMIN desde la Fase 4), así que tiene
        // "empleado:configurar".
        Map<String, Object> ficha = mapOf("horasSemanales", 37.5, "diasVacaciones", 25);

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/empleados/" + empleadoId + "/ficha"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(ficha), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("horasSemanales").asDouble()).isEqualTo(37.5);
        assertThat(body.get("diasVacaciones").asInt()).isEqualTo(25);
    }

    @Test
    @Order(45)
    void elSaldoDeVacacionesRefleja_loQueEscribioLaFicha() throws Exception {
        // La prueba de que el PATCH escribe DE VERDAD en
        // "saldo_vacaciones": hasta la Fase A nadie llamaba nunca a
        // save() sobre esa tabla, así que nada lo comprobaba.
        int anioActual = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Madrid")).getYear();

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias/saldo-vacaciones?anio=" + anioActual),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("diasTotales").asInt()).isEqualTo(25);
    }

    @Test
    @Order(46)
    void empleadoNoPuedeConfigurarFichas_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/empleados/" + empleadoId + "/ficha"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("horasSemanales", 20.0)), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(47)
    void configurarLaFichaDeOtraEmpresa_devuelve403() throws Exception {
        // Aislamiento multi-tenant a mano (ADR 006): no hay filtro
        // automático, cada endpoint compara la empresa.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/empleados/" + empleadoId + "/ficha"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("horasSemanales", 20.0)), authHeaders(gestorOtraEmpresaToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(48)
    void configurarLaFichaConJornadaFueraDeRango_devuelve400() throws Exception {
        // El CHECK de la base dice horas_semanales <= 60; el DTO lo
        // espeja para que esto sea un 400 y no un 500.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/empleados/" + empleadoId + "/ficha"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("horasSemanales", 61.0)), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------
    // 5.c AVISOS DENTRO DE LA APLICACIÓN (Fase A)
    // ------------------------------------------------------------------
    // Los avisos los escribe un listener @Async AFTER_COMMIT, así que
    // hay carrera entre el 200 de la operación y el INSERT: por eso se
    // sondea en vez de comprobar justo después. Lo que se fija aquí es
    // el CONTRATO HTTP; que cada evento produzca su aviso lo comprueba
    // NotificationListenerTest, que es síncrono y determinista.

    private JsonNode esperarAvisosDe(String token) throws Exception {
        JsonNode avisos = null;
        for (int intento = 0; intento < 30; intento++) {
            ResponseEntity<String> response = rest.exchange(
                    url("/api/v1/avisos"),
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token)),
                    String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            avisos = bodyOf(response);
            if (avisos.size() > 0) {
                return avisos;
            }
            Thread.sleep(100);
        }
        return avisos;
    }

    @Test
    @Order(55)
    void elEmpleadoTieneAvisosDeLoQueLeHaPasado() throws Exception {
        JsonNode avisos = esperarAvisosDe(empleadoToken);

        assertThat(avisos.isArray()).isTrue();
        // Al menos la bienvenida del alta (test 10) y la resolución de
        // su ausencia (test 33).
        assertThat(avisos.size()).isGreaterThanOrEqualTo(2);

        JsonNode primero = avisos.get(0);
        assertThat(primero.has("id")).isTrue();
        assertThat(primero.has("tipo")).isTrue();
        assertThat(primero.has("titulo")).isTrue();
        assertThat(primero.has("rutaDestino")).isTrue();
        assertThat(primero.get("leido").asBoolean()).isFalse();
    }

    @Test
    @Order(56)
    void marcarUnAvisoComoLeidoBajaElContadorDeNoLeidos() throws Exception {
        ResponseEntity<String> antes = rest.exchange(
                url("/api/v1/avisos/no-leidos"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(antes.getStatusCode()).isEqualTo(HttpStatus.OK);
        int noLeidosAntes = bodyOf(antes).get("noLeidos").asInt();
        assertThat(noLeidosAntes).isPositive();

        long avisoId = esperarAvisosDe(empleadoToken).get(0).get("id").asLong();
        ResponseEntity<String> marcado = rest.exchange(
                url("/api/v1/avisos/" + avisoId + "/leido"),
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(marcado.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> despues = rest.exchange(
                url("/api/v1/avisos/no-leidos"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(bodyOf(despues).get("noLeidos").asInt()).isEqualTo(noLeidosAntes - 1);
    }

    @Test
    @Order(57)
    void marcarElAvisoDeOtraPersona_devuelve403() throws Exception {
        // Más estricto que el aislamiento entre empresas: el gestor y el
        // empleado son de la MISMA empresa y aun así no pueden tocarse
        // los avisos.
        long avisoDelGestor = esperarAvisosDe(gestorToken).get(0).get("id").asLong();

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/avisos/" + avisoDelGestor + "/leido"),
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(58)
    void marcarTodosLosAvisosDejaElContadorACero() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/avisos/leer-todos"),
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> contador = rest.exchange(
                url("/api/v1/avisos/no-leidos"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(bodyOf(contador).get("noLeidos").asInt()).isZero();
    }

    @Test
    @Order(59)
    void consultarAvisosSinAutenticar_devuelve401() throws Exception {
        // Los avisos no piden authority, pero sí sesión.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/avisos"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // 5.d PERFIL Y DEPARTAMENTOS (Fase B)
    // ------------------------------------------------------------------

    @Test
    @Order(60)
    void empleadoVeSuPerfil_conNombreCompletoEInicialesCalculados() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/perfil"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("email").asText()).isEqualTo(EMAIL_EMPLEADO);
        // CAMBIADO EN 09/2026: el alta ya pide nombre y apellidos por
        // separado, asi que aqui ya NO se llega sin apellidos -- antes se
        // creaba con un unico campo y este test fijaba ese caso.
        //
        // El caso "sin apellidos" sigue existiendo (una cuenta antigua, o
        // unos apellidos borrados desde el perfil) y lo cubre el unitario
        // de EmployeeProfileServiceImpl; aqui se comprueba lo que pasa de
        // verdad por la API hoy: los dos campos, unidos por el servidor.
        assertThat(body.get("apellidos").asText()).isEqualTo("Contract");
        assertThat(body.get("nombreCompleto").asText()).isEqualTo("Empleado Contract");
        // Inicial del nombre + inicial del apellido, calculadas en el
        // servidor para que cada cliente no invente su propia regla.
        assertThat(body.get("iniciales").asText()).isEqualTo("EC");
    }

    @Test
    @Order(61)
    void empleadoActualizaSuPerfil_yElNombreCompletoSeRecalcula() throws Exception {
        Map<String, Object> cambios = mapOf(
                "apellidos", "Contract Pérez",
                "puesto", "Analista",
                "fechaNacimiento", "1995-03-14"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/perfil"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(cambios), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("apellidos").asText()).isEqualTo("Contract Pérez");
        assertThat(body.get("puesto").asText()).isEqualTo("Analista");
        assertThat(body.get("nombreCompleto").asText()).endsWith("Contract Pérez");
        assertThat(body.get("iniciales").asText()).endsWith("C");
    }

    @Test
    @Order(62)
    void unEmpleadoNoPuedeAscenderseConUnPatchASuPerfil() throws Exception {
        // El corazón del diseño de UpdateProfileRequest: rol, jornada y
        // vacaciones NO están en el record, así que Jackson los descarta
        // y no hay forma de tocarlos desde el perfil propio.
        Map<String, Object> intento = mapOf(
                "puesto", "Analista senior",
                "rol", "ADMIN",
                "horasSemanales", 1,
                "diasVacaciones", 99
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/perfil"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(intento), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("rol").asText()).isEqualTo("EMPLEADO");
        assertThat(body.get("diasVacaciones").asInt()).isEqualTo(25);   // el que fijó RRHH en el test 44
        assertThat(body.get("horasSemanales").asDouble()).isNotEqualTo(1.0);
    }

    @Test
    @Order(63)
    void empleadoNoPuedeVerElPerfilDeOtro_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/perfil/1"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(64)
    void gestorCreaUnDepartamentoYSeLoAsignaAlEmpleado() throws Exception {
        ResponseEntity<String> creado = rest.exchange(
                url("/api/v1/departamentos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("nombre", "Operaciones")), authHeaders(gestorToken)),
                String.class
        );
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode departamento = bodyOf(creado);
        assertThat(departamento.get("empleados").asLong()).isZero();
        departamentoId = departamento.get("id").asLong();

        ResponseEntity<String> asignado = rest.exchange(
                url("/api/v1/departamentos/empleados/" + empleadoId),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("departamentoId", departamentoId)), authHeaders(gestorToken)),
                String.class
        );
        assertThat(asignado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(asignado).get("departamentoNombre").asText()).isEqualTo("Operaciones");
    }

    @Test
    @Order(65)
    void crearUnDepartamentoRepetido_devuelve409() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/departamentos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("nombre", "operaciones")), authHeaders(gestorToken)),
                String.class
        );

        // Da igual la caja: "operaciones" y "Operaciones" son el mismo.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(66)
    void borrarUnDepartamentoConGenteDentro_devuelve409ExplicandoCuanta() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/departamentos/" + departamentoId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // El mensaje dice cuántos hay, que es lo que la violación de
        // clave ajena no podría decir.
        assertThat(bodyOf(response).get("detail").asText()).contains("1");
    }

    @Test
    @Order(67)
    void alSacarAlEmpleadoDelDepartamento_yaSePuedeBorrar() throws Exception {
        ResponseEntity<String> sacado = rest.exchange(
                url("/api/v1/departamentos/empleados/" + empleadoId),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("departamentoId", null)), authHeaders(gestorToken)),
                String.class
        );
        assertThat(sacado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(sacado).get("departamentoId").isNull()).isTrue();

        ResponseEntity<String> borrado = rest.exchange(
                url("/api/v1/departamentos/" + departamentoId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(68)
    void empleadoNoPuedeCrearDepartamentos_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/departamentos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("nombre", "Mío")), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // 5.e ADJUNTOS: CV Y FOTO (Fase B2)
    // ------------------------------------------------------------------

    /** Un PDF de verdad en lo que se comprueba: su cabecera. */
    private static byte[] pdfDePrueba() {
        byte[] contenido = new byte[64];
        System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.UTF_8), 0, contenido, 0, 8);
        return contenido;
    }

    private HttpEntity<MultiValueMap<String, Object>> multipart(
            String token, String nombre, String tipo, byte[] contenido) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("fichero", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return nombre;
            }
        });
        cuerpo.add("tipo", tipo);
        return new HttpEntity<>(cuerpo, headers);
    }

    @Test
    @Order(70)
    void empleadoSubeSuCv_devuelve200ConElMimeReal() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/perfil/adjuntos"),
                HttpMethod.POST,
                multipart(empleadoToken, "mi cv.pdf", "CV", pdfDePrueba()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("tipo").asText()).isEqualTo("CV");
        assertThat(body.get("mime").asText()).isEqualTo("application/pdf");
        assertThat(body.get("nombreOriginal").asText()).isEqualTo("mi cv.pdf");
        adjuntoId = body.get("id").asLong();
    }

    @Test
    @Order(71)
    void unEjecutableRenombradoAPdf_devuelve400() throws Exception {
        // "MZ" es la cabecera de un .exe de Windows. El nombre dice .pdf
        // y el Content-Type del multipart también: los dos los elige
        // quien sube, y por eso se mira el contenido.
        byte[] exe = {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/perfil/adjuntos"),
                HttpMethod.POST,
                multipart(empleadoToken, "cv.pdf", "CV", exe),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).get("detail").asText()).contains("contenido");
    }

    @Test
    @Order(72)
    void elCvSeDescargaComoAttachment_conSuContenidoIntacto() throws Exception {
        ResponseEntity<byte[]> response = rest.exchange(
                url("/api/v1/perfil/adjuntos/" + adjuntoId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                byte[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment")
                .contains("mi cv.pdf");
        assertThat(response.getBody()).isEqualTo(pdfDePrueba());
    }

    @Test
    @Order(73)
    void elGestorNoPuedeDescargarUnCvQueNadieLePresento_devuelve403() throws Exception {
        // 🚨 CAMBIADO EN SEPTIEMBRE DE 2026. Este test decia lo contrario:
        // "descargar es de EMPRESA, no de persona, porque un gestor
        // necesita leer el curriculum de su equipo". El motivo era bueno
        // y la regla, demasiado ancha -- los ids son numeros corridos, asi
        // que cualquiera con sesion podia bajarse el CV de toda la
        // plantilla probando numeros.
        //
        // Ahora leer el CV de otra persona exige un motivo nombrado: que
        // se haya presentado a una vacante y a ti te toque valorarla (ver
        // el Order 153, y el ADR 013). Aqui todavia no hay ninguna
        // candidatura, asi que el gestor no tiene nada que hacer con este
        // fichero.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/perfil/adjuntos/" + adjuntoId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(74)
    void alguienDeOtraEmpresaNoPuedeDescargarlo_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/perfil/adjuntos/" + adjuntoId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorOtraEmpresaToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(75)
    void elGestorNoPuedeBorrarleElCvAlEmpleado_devuelve403() throws Exception {
        // La asimetría: leerlo sí, borrárselo no.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/perfil/adjuntos/" + adjuntoId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(76)
    void subirOtroCvReemplazaElAnterior_yElViejoDejaDeExistir() throws Exception {
        ResponseEntity<String> nuevo = rest.exchange(
                url("/api/v1/perfil/adjuntos"),
                HttpMethod.POST,
                multipart(empleadoToken, "cv v2.pdf", "CV", pdfDePrueba()),
                String.class
        );
        assertThat(nuevo.getStatusCode()).isEqualTo(HttpStatus.OK);

        long nuevoId = bodyOf(nuevo).get("id").asLong();
        assertThat(nuevoId).isNotEqualTo(adjuntoId);

        // Un CV vigente por persona: el anterior se ha ido, y sus bytes
        // con él (ON DELETE CASCADE).
        ResponseEntity<String> viejo = rest.exchange(
                url("/api/v1/perfil/adjuntos/" + adjuntoId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(viejo.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> lista = rest.exchange(
                url("/api/v1/perfil/adjuntos"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(bodyOf(lista)).hasSize(1);
        adjuntoId = nuevoId;
    }

    @Test
    @Order(77)
    void elEmpleadoBorraSuPropioCv_devuelve204() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/perfil/adjuntos/" + adjuntoId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ------------------------------------------------------------------
    // 5b. CALENDARIO LABORAL (Fase C)
    // ------------------------------------------------------------------
    // Van sobre 2030, un año que ningún otro test toca: así se prueba de
    // verdad la siembra bajo demanda (la tabla "festivos" empieza vacía,
    // porque DemoDataSeeder solo corre con el perfil "demo") sin que el
    // resultado dependa de en qué año se ejecute la suite.

    @Test
    @Order(100)
    void mirarUnAnioPorPrimeraVez_siembraSusFestivosNacionales() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/calendario?anio=2030&mes=12"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        // Diciembre trae tres nacionales: Constitución, Inmaculada y
        // Navidad. Nadie los ha dado de alta: los ha calculado el
        // servidor al recibir esta petición.
        assertThat(body.get("festivos")).hasSize(3);
        assertThat(body.get("incluyeEquipo").asBoolean()).isFalse();

        JsonNode navidad = body.get("festivos").get(2);
        assertThat(navidad.get("fecha").asText()).isEqualTo("2030-12-25");
        assertThat(navidad.get("ambito").asText()).isEqualTo("NACIONAL");
        // Un nacional es una fila compartida por todas las empresas.
        assertThat(navidad.get("editable").asBoolean()).isFalse();
        festivoNacionalId = navidad.get("id").asLong();
    }

    @Test
    @Order(101)
    void unGestorNoPuedeBorrarUnFestivoNacional_devuelve403() throws Exception {
        // Es la regla que protege a las demás empresas: borrar Navidad
        // desde una se la quitaría del calendario a todas.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/calendario/festivos/" + festivoNacionalId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(response).get("detail").asText()).contains("nacionales");
    }

    @Test
    @Order(102)
    void gestorAnadeUnFestivoLocalYApareceEnSuMes() throws Exception {
        ResponseEntity<String> creado = rest.exchange(
                url("/api/v1/calendario/festivos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "fecha", "2030-05-15",
                        "descripcion", "San Isidro",
                        "ambito", "LOCAL")), authHeaders(gestorToken)),
                String.class
        );

        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode festivo = bodyOf(creado);
        assertThat(festivo.get("editable").asBoolean()).isTrue();
        festivoId = festivo.get("id").asLong();

        // Y lo ve el empleado de la misma empresa, junto al nacional del
        // 1 de mayo.
        ResponseEntity<String> mayo = rest.exchange(
                url("/api/v1/calendario?anio=2030&mes=5"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(bodyOf(mayo).get("festivos")).hasSize(2);
    }

    @Test
    @Order(103)
    void dosFestivosDeEmpresaElMismoDia_devuelve409() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/calendario/festivos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "fecha", "2030-05-15",
                        "descripcion", "Otra cosa",
                        "ambito", "EMPRESA")), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("detail").asText()).contains("San Isidro");
    }

    @Test
    @Order(104)
    void crearUnFestivoConAmbitoNacional_devuelve400() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/calendario/festivos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "fecha", "2030-06-11",
                        "descripcion", "Mi fiesta nacional",
                        "ambito", "NACIONAL")), authHeaders(gestorToken)),
                String.class
        );

        // 400 y no 409: no hay conflicto de estado, es un valor que este
        // endpoint no acepta.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(105)
    void unGestorDeOtraEmpresaNoPuedeTocarNuestrosFestivos_devuelve403() throws Exception {
        // ADR 006: no hay filtro multi-tenant automático, así que esto
        // solo pasa si el endpoint compara empresa_id a mano.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/calendario/festivos/" + festivoId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(gestorOtraEmpresaToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(106)
    void unEmpleadoNoPuedeAnadirFestivos_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/calendario/festivos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "fecha", "2030-08-01",
                        "descripcion", "Me lo pido yo",
                        "ambito", "EMPRESA")), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(107)
    void pedirElEquipoSinPoderVerlo_devuelveLoPropioSinFallar() throws Exception {
        // Un 403 obligaría al cliente a saber su propio rol antes de
        // pedir el mes; el campo "incluyeEquipo" le dice lo que ha
        // recibido, que es lo que necesita para no enseñar un
        // interruptor que no hace nada.
        ResponseEntity<String> delEmpleado = rest.exchange(
                url("/api/v1/calendario?anio=2030&mes=5&equipo=true"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(delEmpleado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(delEmpleado).get("incluyeEquipo").asBoolean()).isFalse();

        ResponseEntity<String> delGestor = rest.exchange(
                url("/api/v1/calendario?anio=2030&mes=5&equipo=true"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(bodyOf(delGestor).get("incluyeEquipo").asBoolean()).isTrue();
    }

    @Test
    @Order(108)
    void unMesFueraDeRango_devuelve400YNoUn500() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/calendario?anio=2030&mes=13"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(109)
    void elGestorPuedeBorrarElFestivoDeSuEmpresa() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/calendario/festivos/" + festivoId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ------------------------------------------------------------------
    // 5c. PROYECTOS Y HORAS POR PROYECTO (Fase D)
    // ------------------------------------------------------------------
    // El test que de verdad importa aquí es el 83: comprueba contra un
    // PostgreSQL real que la restricción EXCLUDE impide que una persona
    // esté en dos proyectos el mismo día. Es una regla que vive en la
    // base de datos, así que ningún test con mocks puede verificarla.

    @Test
    @Order(80)
    void gestorCreaUnProyecto() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/proyectos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "codigo", "NX-CORE",
                        "nombre", "Plataforma",
                        "fechaInicio", "2026-01-01")), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode proyecto = bodyOf(response);
        assertThat(proyecto.get("activo").asBoolean()).isTrue();
        assertThat(proyecto.get("asignados").asLong()).isZero();
        proyectoId = proyecto.get("id").asLong();

        // Un segundo proyecto, para poder intentar el solape en el 83.
        ResponseEntity<String> otro = rest.exchange(
                url("/api/v1/proyectos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "codigo", "NX-APP",
                        "nombre", "Aplicación móvil",
                        "fechaInicio", "2026-01-01")), authHeaders(gestorToken)),
                String.class
        );
        assertThat(otro.getStatusCode()).isEqualTo(HttpStatus.OK);
        otroProyectoId = bodyOf(otro).get("id").asLong();
    }

    @Test
    @Order(81)
    void unCodigoDeProyectoRepetido_devuelve409() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/proyectos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "codigo", "nx-core",
                        "nombre", "Otro",
                        "fechaInicio", "2026-01-01")), authHeaders(gestorToken)),
                String.class
        );

        // Da igual la caja, como con los departamentos.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(82)
    void gestorAsignaAlEmpleadoAlProyecto() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/proyectos/" + proyectoId + "/asignaciones"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "usuarioId", empleadoId,
                        "fechaInicio", "2026-01-01")), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode asignacion = bodyOf(response);
        assertThat(asignacion.get("proyectoCodigo").asText()).isEqualTo("NX-CORE");
        // Sin fecha de fin: sigue asignado.
        assertThat(asignacion.get("fechaFin").isNull()).isTrue();
        asignacionId = asignacion.get("id").asLong();
    }

    @Test
    @Order(83)
    void nadiePuedeEstarEnDosProyectosElMismoDia_devuelve409() throws Exception {
        // La asignación anterior no tiene fecha de fin, así que cubre
        // desde 2026-01-01 hasta el infinito: cualquier fecha posterior
        // choca. Lo impide el EXCLUDE de la base, no el código Java.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/proyectos/" + otroProyectoId + "/asignaciones"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "usuarioId", empleadoId,
                        "fechaInicio", "2026-06-01")), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Y el mensaje dice en qué proyecto está, no "error inesperado".
        assertThat(bodyOf(response).get("detail").asText()).contains("NX-CORE");
    }

    @Test
    @Order(84)
    void alCerrarLaAsignacionAnterior_yaSePuedeAsignarAlOtroProyecto() throws Exception {
        ResponseEntity<String> cerrada = rest.exchange(
                url("/api/v1/proyectos/asignaciones/" + asignacionId),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("fechaFin", "2026-05-31")), authHeaders(gestorToken)),
                String.class
        );
        assertThat(cerrada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(cerrada).get("fechaFin").asText()).isEqualTo("2026-05-31");

        // El relevo es al día siguiente: un rango [.., 31/5] y otro que
        // empieza el 1/6 no se solapan (daterange normaliza el límite
        // superior a exclusivo).
        ResponseEntity<String> nueva = rest.exchange(
                url("/api/v1/proyectos/" + otroProyectoId + "/asignaciones"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "usuarioId", empleadoId,
                        "fechaInicio", "2026-06-01")), authHeaders(gestorToken)),
                String.class
        );
        assertThat(nueva.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(nueva).get("proyectoCodigo").asText()).isEqualTo("NX-APP");
    }

    @Test
    @Order(85)
    void elHistorialDeAsignacionesConservaLasCerradas() throws Exception {
        // Cerrar una asignación NO la borra: es lo que permite que las
        // horas de enero a mayo sigan imputadas a NX-CORE.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/proyectos/empleados/" + empleadoId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode asignaciones = bodyOf(response);
        assertThat(asignaciones).hasSize(2);
    }

    @Test
    @Order(86)
    void unEmpleadoNoPuedeCrearProyectos_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/proyectos"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "codigo", "MIO",
                        "nombre", "Mi proyecto",
                        "fechaInicio", "2026-01-01")), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(87)
    void unGestorDeOtraEmpresaNoPuedeTocarNuestrosProyectos_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/proyectos/" + proyectoId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(gestorOtraEmpresaToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(88)
    void borrarUnProyectoConAsignaciones_devuelve409YSugiereCerrarlo() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/proyectos/" + proyectoId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("detail").asText()).contains("ciérralo");
    }

    @Test
    @Order(89)
    void cerrarUnProyectoNoBorraSuHistorial() throws Exception {
        ResponseEntity<String> cerrado = rest.exchange(
                url("/api/v1/proyectos/" + proyectoId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("activo", false)), authHeaders(gestorToken)),
                String.class
        );
        assertThat(cerrado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(cerrado).get("activo").asBoolean()).isFalse();
        // Sigue teniendo su asignación: cerrar no es borrar.
        assertThat(bodyOf(cerrado).get("asignados").asLong()).isEqualTo(1);

        // Y las horas del mes siguen respondiendo (vacías en este test:
        // los fichajes del contrato no caen en el rango de asignación).
        ResponseEntity<String> horas = rest.exchange(
                url("/api/v1/proyectos/horas?anio=2026&mes=6"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(horas.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(horas).get("mes").asInt()).isEqualTo(6);
    }

    // ------------------------------------------------------------------
    // 5d. HORAS EXTRA (Fase F)
    // ------------------------------------------------------------------
    // Aqui NO se comprueba la deteccion: los avisos los crea el proceso
    // nocturno sobre jornadas reales de mas de nueve horas, y montar eso
    // por HTTP significaria fichar y esperar. Esa parte esta cubierta
    // contra PostgreSQL real en OvertimeServiceIT.
    //
    // Lo que se fija aqui es el REPARTO DE PERMISOS, que en esta fase no
    // es simetrico y es justo lo que un cliente puede romper sin darse
    // cuenta: ver lo tuyo no pide authority, revisar si.

    @Test
    @Order(110)
    void unEmpleadoVeSusHorasExtraSinPermisosDeGestion() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/horas-extra"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).isArray()).isTrue();
    }

    @Test
    @Order(111)
    void unEmpleadoNoVeLaBandejaDelEquipo_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/horas-extra/equipo"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(112)
    void quienRevisaSiVeLaBandejaDelEquipo() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/horas-extra/equipo"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(113)
    void laBolsaAnualSaleDeLas80HorasDelArticulo35() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/horas-extra/bolsa"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 80 h en minutos. Viaja el tope y no solo lo consumido para que
        // el cliente pueda pintar la barra sin llevar la cifra legal
        // escrita en el codigo -- si el convenio la cambia, cambia aqui.
        assertThat(bodyOf(response).get("minutosTope").asInt()).isEqualTo(4800);
        assertThat(bodyOf(response).get("minutosConsumidos").asInt()).isZero();
        assertThat(bodyOf(response).get("minutosDisponibles").asInt()).isEqualTo(4800);
        assertThat(bodyOf(response).get("alLimite").asBoolean()).isFalse();
    }

    @Test
    @Order(114)
    void unEmpleadoNoVeLaBolsaDeOtraPersona_devuelve403() throws Exception {
        // Mirar la bolsa ajena es una operacion de revision aunque solo
        // se lea: son las horas de otro. Se pide la de un id que no es
        // el suyo; da igual de quien sea o si existe, porque el permiso
        // se comprueba antes de ir a buscar a nadie.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/horas-extra/bolsa?usuarioId=" + (empleadoId + 1)),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(115)
    void unEmpleadoNoPuedeRevisarAvisos_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/horas-extra/999999"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("aceptar", true)), authHeaders(empleadoToken)),
                String.class
        );

        // 403 y no 404: el permiso se comprueba antes de ir a buscar el
        // aviso, asi que un 404 aqui filtraria que ese id no existe.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(116)
    void revisarUnAvisoQueNoExiste_devuelve404() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/horas-extra/999999"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("aceptar", true)), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // 5e. CANAL DE DENUNCIAS (Fase G)
    // ------------------------------------------------------------------
    // Lo que se fija aqui son las dos cosas que un cliente puede romper
    // sin enterarse, y que no son "que el endpoint responda":
    //
    //  1. El ANONIMATO end to end. El expediente de una denuncia anonima
    //     no dice quien la puso ni siquiera al ADMIN que la instruye, y
    //     no aparece en "mis denuncias" ni para su propio autor.
    //  2. Que "denuncia:instruir" NO baja de ADMIN. Se comprueba con un
    //     GESTOR real (gestor2Token, Order 28), no con un empleado: el
    //     error facil es dar por hecho que la jerarquia de roles la
    //     reparte hacia abajo como al resto de authorities de gestion.

    @Test
    @Order(120)
    void unEmpleadoPresentaUnaDenunciaAnonimaYRecibeSuCodigo() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/denuncias"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "categoria", "SEGURIDAD",
                        "descripcion", "Las salidas de emergencia del almacen llevan semanas bloqueadas.",
                        "anonima", true)), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = bodyOf(response);
        assertThat(body.get("codigoSeguimiento").asText()).isNotBlank();
        assertThat(body.get("anonima").asBoolean()).isTrue();
        // El aviso de "guardalo, no se puede recuperar" viaja desde el
        // servidor: si dependiera de que el cliente se acuerde de
        // enseñarlo, el primero que lo olvide deja a alguien sin acceso
        // a su propio expediente para siempre.
        assertThat(body.get("avisoImportante").asText()).isNotBlank();

        codigoDenunciaAnonima = body.get("codigoSeguimiento").asText();
    }

    @Test
    @Order(121)
    void elCodigoAbreElExpedienteYNoDiceQuienLaPuso() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/denuncias/seguimiento/" + codigoDenunciaAnonima),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("anonima").asBoolean()).isTrue();
        assertThat(body.get("denunciante").isNull()).isTrue();
        assertThat(body.get("estado").asText()).isEqualTo("RECIBIDA");
        // Recien presentada: quedan 7 dias naturales para acusar recibo.
        assertThat(body.get("diasHastaAcuse").asInt()).isEqualTo(7);

        denunciaAnonimaId = body.get("id").asLong();
    }

    @Test
    @Order(122)
    void unaDenunciaAnonimaNoSaleEnMisDenunciasNiParaSuAutor() throws Exception {
        // Es la contrapartida del anonimato, no un fallo: no hay ningun
        // dato que relacione la denuncia con quien la puso, asi que no
        // se puede listar ni para el. A ella se llega solo con el codigo.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/denuncias/mias"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).isArray()).isTrue();
        assertThat(bodyOf(response)).isEmpty();
    }

    @Test
    @Order(123)
    void unGestorNoVeLaBandejaDelCanal_devuelve403() throws Exception {
        // Con un GESTOR de verdad, no con un empleado: la denuncia puede
        // ser SOBRE el gestor, asi que aqui la jerarquia de roles no
        // reparte la authority hacia abajo como en el resto de la app.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/denuncias"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestor2Token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(124)
    void elAdminSiVeLaBandeja_yLaDenunciaAnonimaSigueSinAutor() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/denuncias"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode bandeja = bodyOf(response);
        assertThat(bandeja).isNotEmpty();
        assertThat(bandeja.get(0).get("anonima").asBoolean()).isTrue();
        // La fila de la bandeja NO lleva la descripcion: una lista se
        // mira de refilon, y el relato de un acoso no es algo que deba
        // aparecer en una vista de conjunto.
        assertThat(bandeja.get(0).has("descripcion")).isFalse();
    }

    @Test
    @Order(125)
    void unEmpleadoNoPuedeAbrirUnaDenunciaPorId_devuelve403() throws Exception {
        // A lo suyo se llega por codigo. Si el id valiera, bastaria con
        // ir probando numeros para leer las denuncias de la empresa.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/denuncias/" + denunciaAnonimaId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(126)
    void unCodigoDeOtraEmpresaDaElMismo404QueUnoInventado() throws Exception {
        // Un 403 aqui confirmaria que el codigo es valido en algun sitio,
        // que es la mitad de lo que necesita quien va probando.
        ResponseEntity<String> ajena = rest.exchange(
                url("/api/v1/denuncias/seguimiento/" + codigoDenunciaAnonima),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorOtraEmpresaToken)),
                String.class
        );
        ResponseEntity<String> inventado = rest.exchange(
                url("/api/v1/denuncias/seguimiento/no-existe-este-codigo"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(ajena.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(inventado.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(127)
    void elAdminAcusaReciboAlPasarlaAInvestigacion() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/denuncias/" + denunciaAnonimaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "EN_INVESTIGACION")),
                        authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("estado").asText()).isEqualTo("EN_INVESTIGACION");
        assertThat(body.get("acuseReciboEn").isNull()).isFalse();
        // Dado el acuse, su plazo se apaga; el de los 3 meses sigue.
        assertThat(body.get("diasHastaAcuse").isNull()).isTrue();
        assertThat(body.get("diasHastaRespuesta").asInt()).isPositive();
    }

    @Test
    @Order(128)
    void cerrarUnaDenunciaSinConclusion_devuelve400() throws Exception {
        // La Ley 2/2023 obliga a RESPONDER, no a dar la razon: archivar
        // sin decir en que quedo es exactamente lo que no vale.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/denuncias/" + denunciaAnonimaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "ARCHIVADA")), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(129)
    void conConclusionSiSeCierra_yDespuesYaNoAdmiteMensajes() throws Exception {
        ResponseEntity<String> cierre = rest.exchange(
                url("/api/v1/denuncias/" + denunciaAnonimaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf(
                        "estado", "RESUELTA",
                        "conclusion", "Comprobado y despejadas las salidas. Se instruye al almacen.")),
                        authHeaders(gestorToken)),
                String.class
        );

        assertThat(cierre.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(cierre).get("resueltaEn").isNull()).isFalse();
        assertThat(bodyOf(cierre).get("diasHastaRespuesta").isNull()).isTrue();

        ResponseEntity<String> mensajeTardio = rest.exchange(
                url("/api/v1/denuncias/seguimiento/" + codigoDenunciaAnonima + "/mensajes"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("texto", "Una cosa mas.")),
                        authHeaders(empleadoToken)),
                String.class
        );

        assertThat(mensajeTardio.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(130)
    void unaDenunciaIDENTIFICADA_siSaleEnMisDenuncias() throws Exception {
        ResponseEntity<String> creada = rest.exchange(
                url("/api/v1/denuncias"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "categoria", "FRAUDE",
                        "descripcion", "Creo que se imputan horas a un proyecto ya cerrado.",
                        "anonima", false)), authHeaders(empleadoToken)),
                String.class
        );
        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bodyOf(creada).get("anonima").asBoolean()).isFalse();

        ResponseEntity<String> mias = rest.exchange(
                url("/api/v1/denuncias/mias"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(mias.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Una, no dos: la anonima de antes sigue sin poder listarse.
        assertThat(bodyOf(mias)).hasSize(1);
        assertThat(bodyOf(mias).get(0).get("categoria").asText()).isEqualTo("FRAUDE");
    }

    @Test
    @Order(131)
    void presentarUnaDenunciaSinDecirSiEsAnonima_devuelve400() throws Exception {
        // Sin defecto a proposito: un false implicito convertiria en
        // delator a quien solo se dejo un campo, y el anonimato no se
        // puede deshacer despues.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/denuncias"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "categoria", "ACOSO",
                        "descripcion", "Los hechos.")), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------
    // 5f. OFERTAS INTERNAS Y CANDIDATURAS (Fase H)
    // ------------------------------------------------------------------
    // Lo que se fija aqui, por encima del reparto de permisos, es LA
    // decision de la fase: la candidatura congela el CV. El test 149 es
    // el que la comprueba de verdad -- se sube otro CV DESPUES de
    // presentarse y el congelado sigue ahi, descargable y con el mismo
    // id. Hasta la fase B2 ese fichero se borraba.
    //
    // Ojo al orden: en el 77 el empleado borro su CV, asi que llega aqui
    // sin ninguno. Eso es lo que hace honesto al 144.

    @Test
    @Order(140)
    void unEmpleadoNoPuedePublicarOfertas_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ofertas"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "titulo", "Puesto inventado",
                        "descripcion", "No deberia crearse.")), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(141)
    void seCreaUnaOfertaYNaceEnBorrador() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ofertas"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf(
                        "titulo", "Backend senior",
                        "descripcion", "Java 21, Spring Boot y PostgreSQL.",
                        "puesto", "Desarrollador/a senior")), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = bodyOf(response);
        // Nace en BORRADOR aunque nadie lo pida: publicar avisa a toda la
        // plantilla, y eso no puede ser el efecto colateral de guardar.
        assertThat(body.get("estado").asText()).isEqualTo("BORRADOR");
        assertThat(body.get("fechaPublicacion").isNull()).isTrue();
        assertThat(body.get("admiteCandidaturas").asBoolean()).isFalse();

        ofertaId = body.get("id").asLong();
    }

    @Test
    @Order(142)
    void unBorradorNoSaleEnElTablonNiSePuedeAbrir() throws Exception {
        ResponseEntity<String> tablon = rest.exchange(
                url("/api/v1/ofertas"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(tablon.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(tablon)).isEmpty();

        // Y por id tampoco: un borrador no existe para la plantilla, en
        // vez de existir y estar prohibido.
        ResponseEntity<String> porId = rest.exchange(
                url("/api/v1/ofertas/" + ofertaId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(porId.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(143)
    void alPublicarlaAparecenLaFechaYElTablon() throws Exception {
        ResponseEntity<String> publicada = rest.exchange(
                url("/api/v1/ofertas/" + ofertaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "ABIERTA")), authHeaders(gestorToken)),
                String.class
        );

        assertThat(publicada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(publicada).get("estado").asText()).isEqualTo("ABIERTA");
        assertThat(bodyOf(publicada).get("fechaPublicacion").isNull()).isFalse();

        ResponseEntity<String> tablon = rest.exchange(
                url("/api/v1/ofertas"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(bodyOf(tablon)).hasSize(1);
        JsonNode fila = bodyOf(tablon).get(0);
        assertThat(fila.get("admiteCandidaturas").asBoolean()).isTrue();
        assertThat(fila.get("yaMePresente").asBoolean()).isFalse();
        // El contador de candidaturas NO viaja a quien no las valora.
        assertThat(fila.get("candidaturas").isNull()).isTrue();
    }

    @Test
    @Order(144)
    void presentarseSinCvDevuelve400() throws Exception {
        // El empleado borro su CV en el Order 77. Presentarse sin CV
        // dejaria al gestor una candidatura vacia, asi que se corta aqui
        // -- y la app lo dice antes de dejar pulsar el boton.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ofertas/" + ofertaId + "/candidaturas"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("carta", "Me presento.")),
                        authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).get("detail").asText()).contains("CV");
    }

    @Test
    @Order(145)
    void conCvSubidoSePresentaYElCvQuedaCongelado() throws Exception {
        ResponseEntity<String> subida = rest.exchange(
                url("/api/v1/perfil/adjuntos"),
                HttpMethod.POST,
                multipart(empleadoToken, "cv-candidatura.pdf", "CV", pdfDePrueba()),
                String.class
        );
        assertThat(subida.getStatusCode()).isEqualTo(HttpStatus.OK);
        cvCongeladoId = bodyOf(subida).get("id").asLong();

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ofertas/" + ofertaId + "/candidaturas"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("carta", "Llevo dos anios en el equipo.")),
                        authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = bodyOf(response);
        assertThat(body.get("estado").asText()).isEqualTo("RECIBIDA");
        // El CV que viaja es el ADJUNTO concreto, no una referencia a la
        // persona: es lo que define la fase.
        assertThat(body.get("cvAdjuntoId").asLong()).isEqualTo(cvCongeladoId);
        assertThat(body.get("puedoValorar").asBoolean()).isFalse();

        candidaturaId = body.get("id").asLong();
    }

    @Test
    @Order(146)
    void presentarseDosVecesALaMismaOferta_devuelve409() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ofertas/" + ofertaId + "/candidaturas"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("carta", "Otra vez.")), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(147)
    void quienValoraVeLaCandidaturaYElContador() throws Exception {
        ResponseEntity<String> candidaturas = rest.exchange(
                url("/api/v1/ofertas/" + ofertaId + "/candidaturas"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(candidaturas.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(candidaturas)).hasSize(1);
        assertThat(bodyOf(candidaturas).get(0).get("puedoValorar").asBoolean()).isTrue();

        ResponseEntity<String> gestion = rest.exchange(
                url("/api/v1/ofertas/gestion"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(gestion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(gestion).get(0).get("candidaturas").asInt()).isEqualTo(1);

        // Y el empleado ve que ya se presento, que es lo que convierte su
        // boton en "ver mi candidatura".
        ResponseEntity<String> tablon = rest.exchange(
                url("/api/v1/ofertas"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(bodyOf(tablon).get(0).get("yaMePresente").asBoolean()).isTrue();
    }

    @Test
    @Order(148)
    void unEmpleadoNoPuedeValorarCandidaturas_devuelve403() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/candidaturas/" + candidaturaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "SELECCIONADA")),
                        authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(149)
    void subirOtroCvNoDestruyeElQueCongeloLaCandidatura() throws Exception {
        // 🚨 ESTE es el test de la fase, y el que habria fallado antes de
        // la V14: hasta ahora subir un CV nuevo BORRABA el anterior, asi
        // que el gestor se quedaba sin el documento sobre el que iba a
        // decidir -- o peor, con otro distinto sin enterarse.
        ResponseEntity<String> nuevo = rest.exchange(
                url("/api/v1/perfil/adjuntos"),
                HttpMethod.POST,
                multipart(empleadoToken, "cv v3.pdf", "CV", pdfDePrueba()),
                String.class
        );
        assertThat(nuevo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(nuevo).get("id").asLong()).isNotEqualTo(cvCongeladoId);

        // El congelado sigue existiendo y se puede descargar.
        ResponseEntity<String> descarga = rest.exchange(
                url("/api/v1/perfil/adjuntos/" + cvCongeladoId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(descarga.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Y la candidatura sigue apuntando a EL MISMO, no al nuevo.
        ResponseEntity<String> candidaturas = rest.exchange(
                url("/api/v1/ofertas/" + ofertaId + "/candidaturas"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(bodyOf(candidaturas).get(0).get("cvAdjuntoId").asLong())
                .isEqualTo(cvCongeladoId);

        // En el perfil, en cambio, solo esta el vigente: el congelado no
        // se lista ni se puede volver a elegir.
        ResponseEntity<String> lista = rest.exchange(
                url("/api/v1/perfil/adjuntos"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(bodyOf(lista)).hasSize(1);
        assertThat(bodyOf(lista).get(0).get("id").asLong()).isNotEqualTo(cvCongeladoId);
    }

    @Test
    @Order(150)
    void descartarSinComentario_devuelve400() throws Exception {
        // Lo lee un companiero, sobre si mismo, en la empresa en la que
        // sigue trabajando maniana.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/candidaturas/" + candidaturaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "DESCARTADA")), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(151)
    void conComentarioSiSeDescarta_yDespuesYaNoSeMueve() throws Exception {
        ResponseEntity<String> descarte = rest.exchange(
                url("/api/v1/candidaturas/" + candidaturaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf(
                        "estado", "DESCARTADA",
                        "comentario", "Buen perfil, buscamos mas recorrido en auditoria.")),
                        authHeaders(gestorToken)),
                String.class
        );

        assertThat(descarte.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(descarte);
        assertThat(body.get("estado").asText()).isEqualTo("DESCARTADA");
        assertThat(body.get("resueltaPor").isNull()).isFalse();
        assertThat(body.get("fechaResolucion").isNull()).isFalse();

        ResponseEntity<String> otraVez = rest.exchange(
                url("/api/v1/candidaturas/" + candidaturaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "EN_PROCESO")), authHeaders(gestorToken)),
                String.class
        );
        assertThat(otraVez.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(152)
    void unaOfertaCerradaNoSeReabre_devuelve409() throws Exception {
        ResponseEntity<String> cierre = rest.exchange(
                url("/api/v1/ofertas/" + ofertaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "CERRADA")), authHeaders(gestorToken)),
                String.class
        );
        assertThat(cierre.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> reapertura = rest.exchange(
                url("/api/v1/ofertas/" + ofertaId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("estado", "ABIERTA")), authHeaders(gestorToken)),
                String.class
        );
        // Reabrirla dejaria a quien ya se presento sin saber si su
        // candidatura sigue contando. Se publica otra.
        assertThat(reapertura.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(153)
    void elGestorLeeElCvPresentado_peroNoElCvActualDeLaPersona() throws Exception {
        // 🚨 El agujero que cierra esta rama: hasta ahora descargar un
        // adjunto solo comprobaba la EMPRESA, y como el id es un numero
        // corrido, cualquiera con sesion podia bajarse el curriculum de
        // sus companieros probando numeros.
        //
        // La linea esta en para que se presento: el CV congelado si, el
        // que esa persona tenga hoy en su perfil no.
        ResponseEntity<String> lista = rest.exchange(
                url("/api/v1/perfil/adjuntos"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        long cvVigenteId = bodyOf(lista).get(0).get("id").asLong();
        assertThat(cvVigenteId).isNotEqualTo(cvCongeladoId);

        // El congelado si: hay una candidatura que lo justifica.
        ResponseEntity<String> presentado = rest.exchange(
                url("/api/v1/perfil/adjuntos/" + cvCongeladoId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(presentado.getStatusCode()).isEqualTo(HttpStatus.OK);

        // El de hoy no: nadie se ha presentado con el.
        ResponseEntity<String> actual = rest.exchange(
                url("/api/v1/perfil/adjuntos/" + cvVigenteId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Y su duenio si, claro.
        ResponseEntity<String> suyo = rest.exchange(
                url("/api/v1/perfil/adjuntos/" + cvVigenteId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        assertThat(suyo.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(154)
    void cerrarSesionesRevocaLosRefreshTokens_peroNoElAccessTokenYaEmitido() throws Exception {
        // Login fresco, y del GESTOR a proposito: este test revoca TODO lo
        // de la cuenta que use, asi que no puede reutilizar un refresh
        // token del que dependa otro test.
        //
        // 🚨 Y no vale el empleado: el Order 90 lo da de baja y nadie lo
        // reactiva, asi que a esta altura su login responde 401 -- que es
        // lo correcto. (Aqui se perdio un ciclo: el test fallaba por su
        // propia premisa, no por el codigo.)
        ResponseEntity<String> login = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(mapOf("email", EMAIL_GESTOR, "contrasena", "password123")),
                        jsonHeaders()),
                String.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String acceso = bodyOf(login).get("token").asText();
        String refresco = bodyOf(login).get("refreshToken").asText();

        ResponseEntity<String> cierre = rest.exchange(
                url("/api/v1/usuario/cerrar-sesiones"),
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(acceso)),
                String.class
        );
        assertThat(cierre.getStatusCode()).isEqualTo(HttpStatus.OK);

        // El refresh token deja de valer: eso es lo que se corta.
        ResponseEntity<String> refresh = rest.postForEntity(
                url("/auth/refresh"),
                new HttpEntity<>(toJson(mapOf("refreshToken", refresco)), jsonHeaders()),
                String.class
        );
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Y el access token YA EMITIDO sigue valiendo hasta que caduque:
        // es un JWT y no se consulta en base. Es la contrapartida asumida,
        // y por eso la pantalla no promete un corte inmediato.
        ResponseEntity<String> perfil = rest.exchange(
                url("/api/v1/perfil"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(acceso)),
                String.class
        );
        assertThat(perfil.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // 6. TOKEN INVÁLIDO
    // ------------------------------------------------------------------

    @Test
    @Order(50)
    void tokenMalformado_devuelve401ConProblemDetail() throws Exception {
        // CORREGIDO EN FASE 4: antes JwtAuthenticationFilter no
        // envolvía en try/catch la lectura del token, así que uno
        // corrupto o caducado reventaba el filtro con una excepción no
        // controlada -- la excepción saltaba ANTES de que el filtro
        // llamara a filterChain.doFilter(), así que nunca pasaba por
        // ExceptionTranslationFilter, y el resultado final era 403 (vía
        // el dispatch a "/error" + denyAll(), verificado empíricamente
        // en la Fase 0), no el 401 que debería devolver. Ahora el token
        // inválido simplemente no autentica la petición, y
        // RestAuthenticationEntryPoint responde 401 con ProblemDetail.
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth("esto-no-es-un-jwt-valido");

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/activo"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(response).get("status").asInt()).isEqualTo(401);
    }

    // ------------------------------------------------------------------
    // 7. REFRESH TOKENS Y LOGOUT (Fase 4)
    // ------------------------------------------------------------------

    @Test
    @Order(51)
    void refresh_conTokenValido_devuelveAccessTokenNuevoQueFunciona() throws Exception {
        // Login fresco para tener un refresh token que nadie más haya
        // tocado (el de loginEmpleado, orden 12, sigue siendo válido,
        // pero cambiar la contraseña en el orden 41 no lo afecta --
        // aquí se hace uno nuevo para que el test sea autocontenido).
        ResponseEntity<String> login = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(mapOf("email", EMAIL_EMPLEADO, "contrasena", "nuevaPassword123")), jsonHeaders()),
                String.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        empleadoRefreshToken = bodyOf(login).get("refreshToken").asText();

        ResponseEntity<String> refresh = rest.postForEntity(
                url("/auth/refresh"),
                new HttpEntity<>(toJson(mapOf("refreshToken", empleadoRefreshToken)), jsonHeaders()),
                String.class
        );

        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(refresh);
        String nuevoAccessToken = body.get("token").asText();
        assertThat(nuevoAccessToken).isNotBlank();
        // El refresh token no rota en esta implementación: sigue siendo el mismo.
        assertThat(body.get("refreshToken").asText()).isEqualTo(empleadoRefreshToken);

        // El access token nuevo funciona de verdad contra un endpoint protegido.
        ResponseEntity<String> activo = rest.exchange(
                url("/api/v1/fichaje/activo"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(nuevoAccessToken)),
                String.class
        );
        assertThat(activo.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(52)
    void logout_revocaElRefreshToken_yUnRefreshPosteriorFalla() throws Exception {
        ResponseEntity<String> logout = rest.postForEntity(
                url("/auth/logout"),
                new HttpEntity<>(toJson(mapOf("refreshToken", empleadoRefreshToken)), jsonHeaders()),
                String.class
        );
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> refreshTrasLogout = rest.postForEntity(
                url("/auth/refresh"),
                new HttpEntity<>(toJson(mapOf("refreshToken", empleadoRefreshToken)), jsonHeaders()),
                String.class
        );
        assertThat(refreshTrasLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(53)
    void login_conDemasiadosIntentos_devuelve429() throws Exception {
        // Aislado del resto del flujo con una IP falsa propia (vía
        // X-Forwarded-For), para no consumir ni verse afectado por el
        // cupo de /auth/login y /auth/register-manager que ya han usado
        // el resto de tests de esta clase (comparten IP real). 10
        // peticiones por minuto (ver LoginRateLimitFilter): la 11ª
        // debe rechazarse.
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Forwarded-For", "203.0.113.55");
        Map<String, Object> credencialesFalsas = mapOf("email", "nadie@nxtime.test", "contrasena", "loquesea");
        HttpEntity<String> peticion = new HttpEntity<>(toJson(credencialesFalsas), headers);

        ResponseEntity<String> ultima = null;
        for (int i = 0; i < 11; i++) {
            ultima = rest.postForEntity(url("/auth/login"), peticion, String.class);
        }

        assertThat(ultima.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ------------------------------------------------------------------
    // 7a. RECUPERAR LA CONTRASEÑA CON UN CÓDIGO (ADR 014)
    // ------------------------------------------------------------------
    // Con una empresa propia y no con las cuentas del flujo: cambiarles la
    // contraseña aquí rompería cualquier test posterior que entre con ellas.
    // Cada petición a /auth va desde su propia IP falsa, por el límite por IP.

    @Test
    @Order(54)
    void recuperar_conUnCorreoSinCuenta_devuelve202_yNoMandaNada() throws Exception {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Forwarded-For", "203.0.113.62");

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/recuperar"),
                new HttpEntity<>(toJson(mapOf("email", "nadie.contract@nxtime.test")), headers),
                String.class
        );

        // El mismo 202 que para un correo con cuenta: si no, bastaría con
        // probar direcciones para saber quién la tiene.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(emailSender, never()).enviarObligatorio(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @Order(55)
    void recuperar_unCodigoIncorrectoDa400_yConElCorrectoSeEntraConLaContrasenaNueva() throws Exception {
        String email = "recupera.contract@nxtime.test";

        HttpHeaders registroHeaders = jsonHeaders();
        registroHeaders.set("X-Forwarded-For", "203.0.113.63");
        ResponseEntity<String> registro = rest.postForEntity(
                url("/auth/register-manager"),
                new HttpEntity<>(toJson(mapOf(
                        "nombreEmpresa", "Recupera Contract SL",
                        "nombre", "Rita",
                        "apellidos", "Levi",
                        "email", email,
                        "contrasena", "olvidada12345")), registroHeaders),
                String.class
        );
        assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders solicitudHeaders = jsonHeaders();
        solicitudHeaders.set("X-Forwarded-For", "203.0.113.64");
        ResponseEntity<String> solicitud = rest.postForEntity(
                url("/auth/recuperar"),
                new HttpEntity<>(toJson(mapOf("email", email)), solicitudHeaders),
                String.class
        );
        assertThat(solicitud.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String codigo = codigoEnviadoA(email);

        String incorrecto = codigo.equals("000000") ? "111111" : "000000";
        ResponseEntity<String> fallo = elegirContrasena(email, incorrecto, "recordada12345", "203.0.113.65");
        assertThat(fallo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(fallo).get("detail").asText()).contains("no es válido o ha caducado");

        ResponseEntity<String> acierto = elegirContrasena(email, codigo, "recordada12345", "203.0.113.66");
        assertThat(acierto.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        HttpHeaders loginHeaders = jsonHeaders();
        loginHeaders.set("X-Forwarded-For", "203.0.113.67");
        ResponseEntity<String> login = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(mapOf("email", email, "contrasena", "recordada12345")), loginHeaders),
                String.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // 7b. ESTADO DE LAS TAREAS NOCTURNAS (paso 5 del piloto)
    // ------------------------------------------------------------------

    @Test
    @Order(85)
    void estadoDeLasTareas_esPublico_yEnUnaBaseRecienCreadaAvisaDeQueNoHanCorrido() throws Exception {
        // Sin token: lo consulta un workflow de GitHub, que no tiene sesión.
        // La base de este test acaba de crearse y las tareas nocturnas no
        // han corrido nunca, así que la respuesta honesta es 503. Un 200
        // aquí querría decir que la comprobación da por buenas tareas que
        // no han existido.
        ResponseEntity<String> respuesta = rest.getForEntity(url("/estado/tareas"), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode cuerpo = bodyOf(respuesta);
        assertThat(cuerpo.get("ok").asBoolean()).isFalse();
        assertThat(cuerpo.get("tareas")).hasSize(2);
        assertThat(cuerpo.get("tareas").get(0).get("ok").asBoolean()).isFalse();
        assertThat(cuerpo.get("tareas").get(0).has("detalle")).isFalse();
    }

    // ------------------------------------------------------------------
    // 8. BAJA DE EMPLEADOS (Fase 4)
    // ------------------------------------------------------------------

    @Test
    @Order(90)
    void gestorDaDeBajaAUnEmpleado_yYaNoPuedeIniciarSesion() throws Exception {
        // Colocado al final a propósito: da de baja al empleado
        // definitivamente, así que no puede ir antes de ningún otro
        // test que necesite volver a iniciar sesión como empleado -- ni
        // de ninguno que use su token, porque una cuenta de baja deja de
        // poder autenticarse. Por eso el bloque de perfil (60-68) va
        // delante y esto se movió del 60 al 90 al añadirlo.
        ResponseEntity<String> baja = rest.exchange(
                url("/api/v1/gestor/empleados/" + empleadoId + "/estado"),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(mapOf("activo", false)), authHeaders(gestorToken)),
                String.class
        );
        assertThat(baja.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> loginTrasBaja = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(mapOf("email", EMAIL_EMPLEADO, "contrasena", "nuevaPassword123")), jsonHeaders()),
                String.class
        );

        // DisabledException (Spring Security, por isEnabled()=false) es
        // una AuthenticationException más -> 401 vía GlobalExceptionHandler.
        assertThat(loginTrasBaja.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
