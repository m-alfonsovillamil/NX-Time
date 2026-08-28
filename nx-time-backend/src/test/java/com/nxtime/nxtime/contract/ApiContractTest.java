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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

    private final TestRestTemplate rest = new TestRestTemplate();
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
                "nombreGestor", "Gestor Contract",
                "email", EMAIL_GESTOR,
                "password", "password123"
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
        assertThat(body.get("nombre").asText()).isEqualTo("Gestor Contract");
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
                "nombreGestor", "Otro Gestor",
                "email", "otro.gestor@nxtime.test",
                "password", "password123"
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
                "nombreGestor", "",
                "email", "esto-no-es-un-email",
                "password", "123" // menos de 6 caracteres
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
                "nombreGestor", "Gestor Otra Empresa",
                "email", EMAIL_GESTOR_OTRA_EMPRESA,
                "password", "password123"
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

    @Test
    @Order(10)
    void gestorCreaEmpleado_devuelve200() throws Exception {
        Map<String, Object> peticion = mapOf(
                "nombre", "Empleado Contract",
                "email", EMAIL_EMPLEADO,
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/empleados"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(11)
    void gestorCreaOtroGestor_devuelve200() throws Exception {
        Map<String, Object> peticion = mapOf(
                "nombre", "Gestor Contract 2",
                "email", EMAIL_GESTOR2,
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/gestores"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
                "motivo", "Un GESTOR normal no debería poder hacer esto."
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/" + registroActivoId),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(correccion), authHeaders(gestor2Token)),
                String.class
        );

        // GESTOR tiene "fichaje:leer:equipo" pero no "fichaje:corregir"
        // (solo RRHH/ADMIN, ver RoleAuthorities) -- corregir un fichaje
        // pasado es una operación de cumplimiento normativo, no de
        // gestión de equipo del día a día.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(29)
    void adminCorrigeFichajeCerrado_devuelve200_yQuedaTrazaCompletaEnAuditoria() throws Exception {
        Map<String, Object> correccion = mapOf(
                "horaEntrada", "2026-01-01T08:00:00Z",
                "horaSalida", "2026-01-01T17:00:00Z",
                "motivo", "El empleado ficho la entrada con 15 minutos de retraso por error del reloj."
        );

        ResponseEntity<String> correctionResponse = rest.exchange(
                url("/api/v1/fichaje/" + registroActivoId),
                HttpMethod.PATCH,
                new HttpEntity<>(toJson(correccion), authHeaders(gestorToken)), // gestorToken es en realidad ADMIN, ver Fase 4
                String.class
        );

        assertThat(correctionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode correctionBody = bodyOf(correctionResponse);
        long fichajeCorregidoId = correctionBody.get("id").asLong();
        // Nunca sobrescribe: el id devuelto es el de una fila NUEVA, no
        // el del fichaje original que se corrigió.
        assertThat(fichajeCorregidoId).isNotEqualTo(registroActivoId);
        assertThat(correctionBody.get("horaEntrada").asText()).startsWith("2026-01-01T08:00:00");

        // El fichaje original queda anulado -> ya no aparece en el
        // historial del empleado (ver TimeEntryRepository.findHistoryByUsuario).
        ResponseEntity<String> historial = rest.exchange(
                url("/api/v1/fichaje/historial"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );
        JsonNode historialBody = bodyOf(historial);
        boolean apareceElOriginal = false;
        for (JsonNode fichaje : historialBody) {
            if (fichaje.get("id").asLong() == registroActivoId) {
                apareceElOriginal = true;
            }
        }
        assertThat(apareceElOriginal).as("el fichaje anulado no debe salir en el historial").isFalse();

        // La línea temporal del fichaje ORIGINAL conserva toda la
        // traza: su creación (INICIO), sus modificaciones (pausa, FIN)
        // y, al final, la corrección -- con motivo, y con quién la hizo.
        ResponseEntity<String> auditoria = rest.exchange(
                url("/api/v1/auditoria/fichaje/" + registroActivoId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );
        assertThat(auditoria.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode trail = bodyOf(auditoria);
        assertThat(trail.isArray()).isTrue();
        assertThat(trail.size()).isGreaterThanOrEqualTo(4); // CREACION + PAUSA_INICIO + PAUSA_FIN + FIN + CORRECCION

        JsonNode primeraEntrada = trail.get(0);
        assertThat(primeraEntrada.get("accion").asText()).isEqualTo("CREACION");
        assertThat(primeraEntrada.get("valorAnterior").isNull()).isTrue();

        JsonNode ultimaEntrada = trail.get(trail.size() - 1);
        assertThat(ultimaEntrada.get("accion").asText()).isEqualTo("CORRECCION");
        assertThat(ultimaEntrada.get("motivo").asText()).contains("retraso por error del reloj");
        assertThat(ultimaEntrada.get("modificadoPor").get("nombre").asText()).isNotBlank();
    }

    @Test
    @Order(291)
    void corregirElMismoFichajeOriginalOtraVez_devuelve409ConProblemDetail() throws Exception {
        // Ya se corrigió en el test anterior (registroActivoId sigue
        // apuntando al ORIGINAL, ahora anulado) -- corregir el mismo
        // original dos veces no tiene sentido: hay que corregir la
        // versión nueva, no la ya sustituida.
        Map<String, Object> correccion = mapOf(
                "horaEntrada", "2026-01-01T08:00:00Z",
                "horaSalida", "2026-01-01T17:00:00Z",
                "motivo", "Segundo intento sobre el mismo original."
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/" + registroActivoId),
                HttpMethod.PATCH,
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
    // 8. BAJA DE EMPLEADOS (Fase 4)
    // ------------------------------------------------------------------

    @Test
    @Order(60)
    void gestorDaDeBajaAUnEmpleado_yYaNoPuedeIniciarSesion() throws Exception {
        // Colocado al final a propósito: da de baja al empleado
        // definitivamente, así que no puede ir antes de ningún otro
        // test que necesite volver a iniciar sesión como empleado.
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
